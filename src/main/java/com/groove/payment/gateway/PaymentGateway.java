package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 연동 추상화 (Strategy 패턴, ARCHITECTURE.md §7.1).
 *
 * <p>v1 은 {@link com.groove.payment.gateway.mock.MockPaymentGateway} 한 구현체만 두며,
 * 실 PG 도입 시 {@code @Profile("prod")} 구현체를 추가하면 호출부 변경 없이 교체된다.
 *
 * <p>결제는 비동기 모델이다 — {@link #request(PaymentRequest)} 는 {@link PaymentStatus#PENDING}
 * 으로 즉시 응답하고, 최종 결과는 웹훅 콜백({@link WebhookNotification}) 으로 전달된다.
 * 웹훅 유실에 대비해 {@link #query(String)} 로 PG 측 상태를 폴링할 수 있다(#W7-4).
 */
public interface PaymentGateway {

    /**
     * 결제를 요청한다.
     *
     * @return 거래 식별자와 {@link PaymentStatus#PENDING} 상태를 담은 응답
     */
    PaymentResponse request(PaymentRequest request);

    /**
     * PG 측 현재 결제 상태를 조회한다 (웹훅 미수신 시 폴링용).
     *
     * @param pgTransactionId {@link PaymentResponse#pgTransactionId()}
     * @return 현재 상태 (처리 중이면 {@link PaymentStatus#PENDING})
     */
    PaymentStatus query(String pgTransactionId);

    /**
     * 결제를 환불한다.
     */
    RefundResponse refund(RefundRequest request);
}
