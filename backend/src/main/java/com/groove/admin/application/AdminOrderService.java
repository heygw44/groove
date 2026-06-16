package com.groove.admin.application;

import com.groove.admin.application.RefundSteps.RefundPrep;
import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderSpecifications;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.gateway.GatewayRefunds;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 주문 조회 / 상태 강제 전환 / 환불 오케스트레이션 (이슈 #69, PRD §5.3·§6.9, G2 게이트).
 *
 * 주문(order)·결제(payment) 두 도메인을 조율하므로 어느 한쪽 모듈이 아닌 admin 모듈에 둔다 — Aggregate 간 조율은
 * 도메인이 아닌 ApplicationService 책임이라는 기존 패턴(PaymentCallbackService, OrderService.cancel 의 재고 복원)과
 * 동일.
 *
 * 상태 강제 전환의 범위: changeStatus 는 부수효과(재고 복원·환불)가 없는 전진 전이(FORCEABLE_TARGETS)만 허용한다.
 * 취소(CANCELLED)·결제실패(PAYMENT_FAILED)·결제완료(PAID) 로의 전환은 재고/결제 상태가 함께 바뀌어야 하므로 이 메서드로
 * 처리하지 않는다 — 취소·환불은 refund, 결제 확정은 웹훅/폴링 경로(PaymentCallbackService)가 담당한다. 허용 대상이라도
 * 실제 전이가 OrderStatus.canTransitionTo 위반이면 409(IllegalStateTransitionException).
 *
 * 환불(#237): PG 환불 호출을 트랜잭션 밖으로 분리한다 — {@code refund} 는 비트랜잭션 오케스트레이터로서
 * {@link RefundSteps#prepare 검증+잠금(tx)} → PG {@code refund()}(트랜잭션 밖, 멱등 키) →
 * {@link RefundSteps#apply 상태 반영+보상(tx)} 순으로 호출한다. PG 호출 동안 DB 커넥션/비관적 락을 점유하지 않는다
 * (기존 한계 제거). 이중 보상 방지·prepare/apply 윈도우 근거는 {@link RefundSteps} Javadoc 참조. 쿠폰 복원은
 * OrderService.cancel 과 대칭으로 호출되어야 하며 누락 시 환불된 주문의 쿠폰이 USED 인 채로 남는다 (이슈 #91 HIGH 리스크).
 *
 * 멱등: 이미 REFUNDED 인 결제에 재요청하면 PG 호출/상태 전이/재고 복원 없이 현재 상태로 응답한다 (이슈 #69 DoD
 * "중복 환불 요청 무해").
 */
@Service
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    /**
     * PATCH /status 로 강제 전환 가능한 대상 상태 — 재고/결제 부수효과가 없는 전진 전이만.
     * (PENDING/PAID/PAYMENT_FAILED/CANCELLED 는 의도적으로 제외 — 위 클래스 Javadoc 참조.)
     */
    private static final Set<OrderStatus> FORCEABLE_TARGETS =
            EnumSet.of(OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    private final RefundSteps refundSteps;

    public AdminOrderService(OrderRepository orderRepository,
                             PaymentGateway paymentGateway,
                             RefundSteps refundSteps) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
        this.refundSteps = refundSteps;
    }

    /**
     * 전체 주문 목록 — 상태/회원/기간 필터 + 페이징. 들어온 필터만 골라 AND 결합한다 (없으면 전체).
     * 회원 주문 목록(OrderService.listForMember)과 동일하게 컬렉션 fetch join 을 두지 않고(Order.getItems 의
     * @BatchSize 활용) 트랜잭션 안에서 items 를 강제 초기화한다 — 컨트롤러의 SUMMARY 응답 매핑(itemCount) 시점
     * LazyInitializationException 회피.
     */
    @Transactional(readOnly = true)
    public Page<Order> list(AdminOrderSearchCriteria criteria, Pageable pageable) {
        List<Specification<Order>> specs = new ArrayList<>();
        if (criteria.status() != null) {
            specs.add(OrderSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.memberId() != null) {
            specs.add(OrderSpecifications.hasMemberId(criteria.memberId()));
        }
        if (criteria.from() != null) {
            specs.add(OrderSpecifications.createdAtFrom(criteria.from()));
        }
        if (criteria.to() != null) {
            specs.add(OrderSpecifications.createdAtBefore(criteria.to()));
        }
        Page<Order> page = orderRepository.findAll(Specification.allOf(specs), pageable);
        // 응답 매핑(AdminOrderSummaryResponse.from) 이 컨트롤러에서 detached 엔티티의 items.size() 를 본다 —
        // spring.jpa.open-in-view=false 이므로 트랜잭션 종료 전에 items 를 일괄 강제 초기화한다
        // (@BatchSize 가 N+1 을 IN 쿼리 1번으로 흡수). 응답 DTO 에 다른 LAZY 연관 접근을 추가할 거면 여기도 함께
        // 초기화해야 한다.
        page.forEach(order -> order.getItems().size());
        return page;
    }

    /** 주문 상세 (관리자) — 회원/게스트 주문 모두 조회 가능 (소유자 검증 없음). */
    @Transactional(readOnly = true)
    public Order findDetail(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
    }

    /**
     * 주문 상태 강제 전환. 사유는 컨트롤러가 필수로 강제한다 (운영 감사 추적).
     *
     * @throws DomainException                  허용 대상이 아닌 상태로의 전환 시도 (422 DOMAIN_RULE_VIOLATION)
     * @throws IllegalStateTransitionException  허용 대상이지만 현재 상태에서 불법 전이 (409
     * ORDER_INVALID_STATE_TRANSITION)
     * @throws OrderNotFoundException           주문 미존재 (404)
     */
    @Transactional
    public Order changeStatus(String orderNumber, OrderStatus target, String reason) {
        if (!FORCEABLE_TARGETS.contains(target)) {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "이 상태로의 강제 전환은 지원하지 않습니다: " + target + " (취소·환불은 POST /api/v1/admin/orders/{orderNumber}/refund 를 사용하세요)");
        }
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        OrderStatus from = order.getStatus();
        order.changeStatus(target, reason);
        log.info("관리자 주문 상태 강제 전환: order={}, {} -> {}, reason='{}'", orderNumber, from, target, reason);
        return order;
    }

    /**
     * 환불 처리 — PG 환불 + Payment REFUNDED + Order CANCELLED + 발송 전 배송 CANCELLED(#233) + 재고 복원.
     * 멱등(이미 환불됨이면 부수효과 없음). PG 호출은 트랜잭션 밖에서 멱등 키로 수행한다 (#237).
     *
     * @throws OrderNotFoundException          주문 미존재 (404)
     * @throws PaymentNotFoundException        해당 주문에 결제 없음 (404)
     * @throws PaymentNotRefundableException   결제가 PAID 가 아님 — PENDING/FAILED (409 PAYMENT_NOT_REFUNDABLE)
     * @throws IllegalStateTransitionException 주문이 CANCELLED 로 전이 불가 (SHIPPED 이후 등, 409)
     * @throws PaymentGatewayException         PG 환불 호출 실패 (502)
     */
    public RefundResult refund(String orderNumber, String reason) {
        RefundPrep prep = refundSteps.prepare(orderNumber);
        if (prep.isAlreadyRefunded()) {
            log.info("관리자 환불: 이미 환불됨 order={} — 멱등 응답", orderNumber);
            return prep.alreadyRefundedResult();
        }
        // PG 환불은 트랜잭션 밖 — prepare 가 비관적 락을 해제하고 커밋한 뒤 호출한다. 같은 멱등 키로 PG 가 dedup 한다.
        RefundResponse pgResponse = callGatewayRefund(prep, reason);
        return refundSteps.apply(orderNumber, reason, pgResponse.refundedAt());
    }

    private RefundResponse callGatewayRefund(RefundPrep prep, String reason) {
        RefundRequest request = new RefundRequest(
                prep.pgTransactionId(), prep.amount(), reason, prep.refundIdempotencyKey());
        return GatewayRefunds.refund(paymentGateway, request);
    }
}
