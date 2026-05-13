package com.groove.admin.application;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderSpecifications;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.exception.PaymentGatewayException;
import com.groove.payment.exception.PaymentNotFoundException;
import com.groove.payment.exception.PaymentNotRefundableException;
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
 * 관리자 주문 조회 / 상태 강제 전환 / 환불 트랜잭션 경계 (이슈 #69, PRD §5.3·§6.9, G2 게이트).
 *
 * <p>주문(order)·결제(payment) 두 도메인을 조율하므로 어느 한쪽 모듈이 아닌 {@code admin} 모듈에 둔다 —
 * Aggregate 간 조율은 도메인이 아닌 ApplicationService 책임이라는 기존 패턴({@code PaymentCallbackService},
 * {@code OrderService#cancel} 의 재고 복원)과 동일하다.
 *
 * <h2>상태 강제 전환의 범위</h2>
 * <p>{@link #changeStatus} 는 부수효과(재고 복원·환불)가 없는 <b>전진 전이</b>({@link #FORCEABLE_TARGETS})만
 * 허용한다. 취소(CANCELLED)·결제실패(PAYMENT_FAILED)·결제완료(PAID) 로의 전환은 재고/결제 상태가 함께
 * 바뀌어야 하므로 이 메서드로 처리하지 않는다 — 취소·환불은 {@link #refund}, 결제 확정은 웹훅/폴링 경로
 * ({@code PaymentCallbackService})가 담당한다. 허용 대상이라도 실제 전이가 {@link OrderStatus#canTransitionTo}
 * 위반이면 409({@link IllegalStateTransitionException}).
 *
 * <h2>환불</h2>
 * <p>{@link #refund} 는 단일 트랜잭션에서 PG {@code refund()} 호출 + {@code Payment} REFUNDED 전이 +
 * {@code Order} CANCELLED 전이 + 각 라인 재고 복원을 수행한다 ({@code PaymentCallbackService} 의 FAILED
 * 보상 트랜잭션과 같은 패턴). 어느 단계든 실패하면 트랜잭션 전체가 롤백된다. PG 호출이 트랜잭션 안에서
 * 일어나 DB 커넥션을 점유하는 한계는 {@code PaymentService} 와 동일하게 v1 Mock 지연이 짧아 허용한다.
 *
 * <p>멱등: 이미 {@code REFUNDED} 인 결제에 재요청하면 PG 호출/상태 전이/재고 복원 없이 현재 상태로 응답한다
 * (이슈 #69 DoD "중복 환불 요청 무해"). 동시 이중 제출 race 는 {@code OrderService#place} 의 재고 차감과
 * 동일하게 v1 에서 노출된 채로 둔다 — 관리자 단건 조작이라 충돌 확률이 무시 가능하며, 만에 하나 두 번째가
 * {@code Payment.markRefunded()} 의 방어선에 걸려도 트랜잭션 롤백으로 끝난다.
 */
@Service
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    /**
     * {@code PATCH /status} 로 강제 전환 가능한 대상 상태 — 재고/결제 부수효과가 없는 전진 전이만.
     * (PENDING/PAID/PAYMENT_FAILED/CANCELLED 는 의도적으로 제외 — 위 클래스 Javadoc 참조.)
     */
    private static final Set<OrderStatus> FORCEABLE_TARGETS =
            EnumSet.of(OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public AdminOrderService(OrderRepository orderRepository,
                             PaymentRepository paymentRepository,
                             PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 전체 주문 목록 — 상태/회원/기간 필터 + 페이징. 들어온 필터만 골라 AND 결합한다 (없으면 전체).
     *
     * <p>회원 주문 목록({@code OrderService#listForMember})과 동일하게 컬렉션 fetch join 을 두지 않고
     * ({@link Order#getItems items} 의 {@code @BatchSize} 활용) 트랜잭션 안에서 items 를 강제 초기화한다 —
     * 컨트롤러의 SUMMARY 응답 매핑({@code itemCount}) 시점 {@code LazyInitializationException} 회피.
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
        page.forEach(order -> order.getItems().size());
        return page;
    }

    /**
     * 주문 상세 (관리자) — 회원/게스트 주문 모두 조회 가능 (소유자 검증 없음).
     */
    @Transactional(readOnly = true)
    public Order findDetail(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
    }

    /**
     * 주문 상태 강제 전환. 사유는 컨트롤러가 필수로 강제한다 (운영 감사 추적).
     *
     * @throws DomainException                  허용 대상이 아닌 상태로의 전환 시도 (422 {@code DOMAIN_RULE_VIOLATION})
     * @throws IllegalStateTransitionException  허용 대상이지만 현재 상태에서 불법 전이 (409 {@code ORDER_INVALID_STATE_TRANSITION})
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
     * 환불 처리 — PG 환불 + Payment REFUNDED + Order CANCELLED + 재고 복원. 멱등(이미 환불됨이면 부수효과 없음).
     *
     * @throws OrderNotFoundException          주문 미존재 (404)
     * @throws PaymentNotFoundException        해당 주문에 결제 없음 (404)
     * @throws PaymentNotRefundableException   결제가 PAID 가 아님 — PENDING/FAILED (409 {@code PAYMENT_NOT_REFUNDABLE})
     * @throws IllegalStateTransitionException 주문이 CANCELLED 로 전이 불가 (SHIPPED 이후 등, 409)
     * @throws PaymentGatewayException         PG 환불 호출 실패 (502)
     */
    @Transactional
    public RefundResult refund(String orderNumber, String reason) {
        Order order = orderRepository.findWithAlbumsByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(PaymentNotFoundException::new);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("관리자 환불: 이미 환불됨 order={}, paymentId={} — 멱등 응답", orderNumber, payment.getId());
            return RefundResult.alreadyRefunded(order, payment);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentNotRefundableException(payment.getStatus());
        }
        if (!order.getStatus().canTransitionTo(OrderStatus.CANCELLED)) {
            // 배송 시작(SHIPPED) 이후 등 — 환불해도 주문을 취소 상태로 둘 수 없으므로 차단 (DoD: 합법 전이만 허용).
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }

        RefundResponse pgResponse = callGatewayRefund(payment, reason);
        payment.markRefunded();
        order.changeStatus(OrderStatus.CANCELLED, reason);
        int restored = 0;
        for (OrderItem item : order.getItems()) {
            item.getAlbum().adjustStock(item.getQuantity());
            restored++;
        }
        log.info("관리자 환불 완료: order={}, paymentId={}, amount={}, 재고복원 {}건",
                orderNumber, payment.getId(), payment.getAmount(), restored);
        return RefundResult.refunded(order, payment, pgResponse.refundedAt());
    }

    private RefundResponse callGatewayRefund(Payment payment, String reason) {
        try {
            return paymentGateway.refund(new RefundRequest(payment.getPgTransactionId(), payment.getAmount(), reason));
        } catch (RuntimeException gatewayFailure) {
            throw new PaymentGatewayException(gatewayFailure);
        }
    }
}
