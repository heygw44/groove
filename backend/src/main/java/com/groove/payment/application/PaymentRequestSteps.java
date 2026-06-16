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
 * 결제 요청의 트랜잭션 단계 협력 빈 — prepare(검증, readOnly tx) / persist(PENDING 저장, tx).
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
     * 검증 + 기존 결제 멱등 판정(readOnly tx). PG 호출/영속화에 필요한 원시값만 반환한다.
     * 주문 로딩 + 소유 검증(불일치/익명 → 404) → 탈퇴 회원 차단 → 기존 결제 있으면 반환 → 비-PENDING 409 → payable ≤ 0 422.
     */
    @Transactional(readOnly = true)
    PaymentRequestPrep prepare(Long callerMemberId, PaymentCreateRequest request) {
        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (!canRequestPaymentFor(order, callerMemberId)) {
            // 타 회원 주문 또는 회원 주문에 익명 접근 — 404 로 통일.
            throw new OrderNotFoundException();
        }
        // 탈퇴(soft delete)한 회원 차단 — 게스트 결제(callerMemberId == null)는 제외.
        if (callerMemberId != null && !memberRepository.existsByIdAndDeletedAtIsNull(callerMemberId)) {
            throw new MemberNotFoundException();
        }

        Payment existing = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            // 이미 접수된 결제가 있으면 그대로 반환(주문 레벨 멱등).
            return PaymentRequestPrep.existing(PaymentApiResponse.from(existing));
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        // 청구액 payable = totalAmount − discountAmount. payable <= 0 이면 거부한다.
        long payable = order.getPayableAmount();
        if (payable <= 0) {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "결제할 금액이 없는 주문입니다: " + order.getOrderNumber());
        }
        return PaymentRequestPrep.proceed(order.getId(), order.getOrderNumber(), payable);
    }

    /**
     * PG 응답 직후 Payment(PENDING) 영속화(별도 tx). saveAndFlush 로 uk_payment_order 충돌을 즉시 드러낸다.
     */
    @Transactional
    PaymentApiResponse persist(PaymentRequestPrep prep, PaymentMethod method, PaymentResponse pgResponse) {
        Order order = orderRepository.findById(prep.orderId()).orElseThrow(OrderNotFoundException::new);
        // 주문이 PENDING 이 아니면 결제를 만들지 않는다.
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

    /** 주문에 이미 접수된 결제를 새 트랜잭션에서 재조회한다(uk_payment_order 충돌 복원용). */
    @Transactional(readOnly = true)
    Optional<PaymentApiResponse> findExistingForOrder(long orderId) {
        return paymentRepository.findByOrderId(orderId).map(PaymentApiResponse::from);
    }

    /** 게스트 주문은 익명 호출자도 결제 시작 가능, 회원 주문은 본인만. */
    private boolean canRequestPaymentFor(Order order, Long callerMemberId) {
        if (order.isGuestOrder()) {
            return true;
        }
        return order.getMemberId().equals(callerMemberId);
    }

    /** prepare 결과 — 기존 결제 멱등 반환(existingResponse non-null) 또는 신규 접수 진행(orderId/orderNumber/payable). */
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
