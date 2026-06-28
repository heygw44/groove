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

/** 결제 요청의 트랜잭션 단계 협력 빈. prepare(검증, readOnly tx) 와 persist(PENDING 저장, tx). */
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

    /** 검증 + 기존 결제 멱등 판정(readOnly tx). PG 호출/영속화에 필요한 원시값만 반환한다. */
    @Transactional(readOnly = true)
    PaymentRequestPrep prepare(Long callerMemberId, PaymentCreateRequest request) {
        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                .orElseThrow(OrderNotFoundException::new);
        if (!canRequestPaymentFor(order, callerMemberId)) {
            // 타 회원 주문·회원 주문 익명 접근은 404 로 통일.
            throw new OrderNotFoundException();
        }
        // 탈퇴(soft delete) 회원 차단. 게스트 결제(callerMemberId == null)는 제외.
        if (callerMemberId != null && !memberRepository.existsByIdAndDeletedAtIsNull(callerMemberId)) {
            throw new MemberNotFoundException();
        }

        Payment existing = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (existing != null) {
            // 이미 접수된 결제는 그대로 반환(주문 레벨 멱등). 콜백 토큰도 실어 checkout 멱등 경로가
            // successUrl 재구성 시 재조회 없이 쓰게 한다.
            return PaymentRequestPrep.existing(PaymentApiResponse.from(existing), existing.getCallbackToken());
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        long payable = order.getPayableAmount();
        if (payable <= 0) {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "결제할 금액이 없는 주문입니다: " + order.getOrderNumber());
        }
        return PaymentRequestPrep.proceed(order.getId(), order.getOrderNumber(), payable);
    }

    @Transactional
    PaymentApiResponse persist(PaymentRequestPrep prep, PaymentMethod method, PaymentResponse pgResponse) {
        return persist(prep, method, pgResponse, null);
    }

    /**
     * PG 응답 직후 Payment(PENDING) 영속화(별도 tx). saveAndFlush 로 uk_payment_order 충돌을 즉시 드러낸다.
     * callbackToken 은 토스 콜백 검증용 결제별 토큰. null 허용(레거시 경로).
     */
    @Transactional
    PaymentApiResponse persist(PaymentRequestPrep prep, PaymentMethod method, PaymentResponse pgResponse,
                               String callbackToken) {
        Order order = orderRepository.findById(prep.orderId()).orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.PAID);
        }
        Payment payment = paymentRepository.saveAndFlush(Payment.initiate(
                order, prep.payable(), method, pgResponse.provider(), pgResponse.pgTransactionId(), callbackToken));
        log.info("결제 접수: paymentId={}, order={}, amount={}, method={}, pgTx={}",
                payment.getId(), prep.orderNumber(), payment.getAmount(), payment.getMethod(),
                payment.getPgTransactionId());
        return PaymentApiResponse.from(payment);
    }

    /** uk_payment_order 충돌 복원용. 콜백 토큰도 실어 토스 복원 경로가 토큰 재조회 없이 successUrl 을 재구성하게 한다. */
    @Transactional(readOnly = true)
    Optional<PaymentRequestPrep> findExistingForOrder(long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(p -> PaymentRequestPrep.existing(PaymentApiResponse.from(p), p.getCallbackToken()));
    }

    /**
     * 게스트 주문은 익명 호출자도 결제 시작 가능, 회원 주문은 본인만.
     * 게스트는 본인 확인이 불가해 orderNumber 만 알면 누구나 콜백 토큰을 받는다(토큰이 비밀이 아님).
     * 잔존 노출은 confirm 관문(금액 위변조 검증 + 유효 paymentKey)이 한정한다. 회원 주문은 memberId 일치로 토큰이 비밀이 된다.
     */
    private boolean canRequestPaymentFor(Order order, Long callerMemberId) {
        if (order.isGuestOrder()) {
            return true;
        }
        return order.getMemberId().equals(callerMemberId);
    }

    /**
     * prepare 결과. 기존 결제 멱등 반환(existingResponse non-null) 또는 신규 접수 진행.
     * callbackToken 은 멱등 반환 경로에서만 채워진다(레거시 결제면 null). 신규 진행 경로는 null.
     */
    record PaymentRequestPrep(PaymentApiResponse existingResponse, Long orderId, String orderNumber, long payable,
                              String callbackToken) {

        static PaymentRequestPrep existing(PaymentApiResponse response, String callbackToken) {
            return new PaymentRequestPrep(response, null, null, 0L, callbackToken);
        }

        static PaymentRequestPrep proceed(Long orderId, String orderNumber, long payable) {
            return new PaymentRequestPrep(null, orderId, orderNumber, payable, null);
        }

        boolean isExisting() {
            return existingResponse != null;
        }
    }
}
