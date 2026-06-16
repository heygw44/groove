package com.groove.payment.application;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.payment.api.dto.PaymentApiResponse;
import com.groove.payment.api.dto.PaymentCreateRequest;
import com.groove.payment.domain.Payment;
import com.groove.payment.domain.PaymentMethod;
import com.groove.payment.domain.PaymentRepository;
import com.groove.payment.gateway.PaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 결제 요청의 트랜잭션 단계 — PG 호출을 트랜잭션 밖으로 분리하기 위한 협력 빈 (#237).
 *
 * <p>{@link PaymentService#requestPayment} 는 비트랜잭션 오케스트레이터로서 {@code prepare(검증, readOnly tx)} →
 * PG {@code request()}(트랜잭션 밖) → {@code persist(PENDING 저장, tx)} 순으로 이 빈의 메서드를 호출한다.
 * 같은 빈 안에서 {@code @Transactional} 메서드를 자기호출하면 프록시를 우회해 트랜잭션이 적용되지 않으므로,
 * 트랜잭션 경계를 갖는 두 단계를 별도 빈으로 분리했다. 이렇게 하면 PG 호출 동안 DB 커넥션을 점유하지 않는다
 * (기존 한계: PaymentService 가 PG 호출을 {@code @Transactional} 안에서 수행 → 실 PG 전환 시 커넥션 풀 고갈).
 */
@Component
class PaymentRequestSteps {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestSteps.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    PaymentRequestSteps(PaymentRepository paymentRepository,
                        OrderRepository orderRepository,
                        MemberRepository memberRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * 검증 + 기존 결제 멱등 판정 (readOnly 트랜잭션). PG 호출은 이 트랜잭션이 커밋(커넥션 해제)된 뒤에 일어나므로,
     * 엔티티를 트랜잭션 밖으로 흘리지 않고 PG 호출/영속화에 필요한 원시값만 추출해 반환한다.
     *
     * <p>처리: 주문 로딩 + 회원 주문이면 호출자 소유 검증(불일치/익명 → 404) → 토큰 유효기간 내 탈퇴(soft delete)
     * 회원 차단(#187) → 이미 접수된 결제가 있으면 그대로 반환(주문 레벨 멱등) → 주문이 PENDING 아니면 409 →
     * payable ≤ 0 이면 422.
     */
    @Transactional(readOnly = true)
    PaymentRequestPrep prepare(Long callerMemberId, PaymentCreateRequest request) {
        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (!canRequestPaymentFor(order, callerMemberId)) {
            // 타 회원 주문 또는 회원 주문에 익명 접근 — 존재 노출 회피를 위해 404 로 통일.
            throw new OrderNotFoundException();
        }
        // 토큰 유효기간 내 탈퇴(soft delete)한 회원이 자신의 PENDING 주문을 결제하는 윈도우를 차단한다
        // (#187, #171·주문 생성과 일관) — 게스트 결제(callerMemberId == null)는 제외.
        if (callerMemberId != null && !memberRepository.existsByIdAndDeletedAtIsNull(callerMemberId)) {
            throw new MemberNotFoundException();
        }

        Payment existing = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            // 같은 주문에 이미 접수된 결제가 있으면 그대로 반환한다(주문 레벨 멱등, uk_payment_order 충돌 사전 회피).
            return PaymentRequestPrep.existing(PaymentApiResponse.from(existing));
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        // 청구액은 payable = totalAmount − discountAmount (#91). 쿠폰 미적용 주문은 payable == totalAmount.
        // payable <= 0 가드는 v1 전액할인 미지원 정책의 결제 도메인 표현이다 (docs/plans/coupon-system.md §결정).
        long payable = order.getPayableAmount();
        if (payable <= 0) {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "결제할 금액이 없는 주문입니다: " + order.getOrderNumber());
        }
        return PaymentRequestPrep.proceed(order.getId(), order.getOrderNumber(), payable);
    }

    /**
     * PG 응답 직후 Payment(PENDING) 영속화 (별도 트랜잭션). {@code saveAndFlush} 로 {@code uk_payment_order}
     * 충돌을 즉시 드러낸다 — 충돌(동시 다른 키로 같은 주문 결제가 prepare/persist 사이에 접수됨)은 호출자가
     * 새 트랜잭션에서 기존 결제를 재조회해 주문 레벨 멱등으로 복원한다({@link #findExistingForOrder}).
     */
    @Transactional
    PaymentApiResponse persist(PaymentRequestPrep prep, PaymentMethod method, PaymentResponse pgResponse) {
        Order order = orderRepository.findById(prep.orderId()).orElseThrow(OrderNotFoundException::new);
        // PG 호출은 트랜잭션 밖이라 prepare 와 persist 사이에 주문이 다른 경로로 전이(취소/결제실패 등)됐을 수 있다.
        // PENDING 이 아니면 결제를 만들지 않는다 — 종착/비-PENDING 주문에 PENDING 결제가 붙는 정합성 깨짐을 차단(#237).
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        Payment payment = paymentRepository.saveAndFlush(Payment.initiate(
                order, prep.payable(), method, pgResponse.provider(), pgResponse.pgTransactionId()));
        log.info("결제 접수: paymentId={}, order={}, amount={}, method={}, pgTx={}",
                payment.getId(), prep.orderNumber(), payment.getAmount(), payment.getMethod(),
                payment.getPgTransactionId());
        return PaymentApiResponse.from(payment);
    }

    /**
     * 주문에 이미 접수된 결제를 새 트랜잭션에서 재조회한다 — {@code persist} 의 {@code uk_payment_order} 충돌
     * 복원용. 충돌이 난 트랜잭션은 rollback-only 로 오염되므로 같은 트랜잭션에서 재조회할 수 없어 분리한다.
     */
    @Transactional(readOnly = true)
    Optional<PaymentApiResponse> findExistingForOrder(long orderId) {
        return paymentRepository.findByOrderId(orderId).map(PaymentApiResponse::from);
    }

    /** 게스트 주문은 익명 호출자도 결제 시작 가능 (API.md — POST /payments 는 Public). 회원 주문은 본인만. */
    private boolean canRequestPaymentFor(Order order, Long callerMemberId) {
        if (order.isGuestOrder()) {
            return true;
        }
        return order.getMemberId().equals(callerMemberId);
    }

    /**
     * prepare 결과 — 기존 결제 멱등 반환({@code existingResponse} non-null) 또는 신규 접수 진행
     * ({@code orderId/orderNumber/payable}). PG 호출은 readOnly 트랜잭션 밖에서 이뤄지므로 엔티티가 아닌
     * 원시값만 담는다.
     */
    record PaymentRequestPrep(PaymentApiResponse existingResponse, Long orderId, String orderNumber, long payable) {

        static PaymentRequestPrep existing(PaymentApiResponse response) {
            return new PaymentRequestPrep(response, null, null, 0L);
        }

        static PaymentRequestPrep proceed(Long orderId, String orderNumber, long payable) {
            return new PaymentRequestPrep(null, orderId, orderNumber, payable);
        }

        boolean isExisting() {
            return existingResponse != null;
        }
    }
}
