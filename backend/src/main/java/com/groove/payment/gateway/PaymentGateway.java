package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 연동 추상화 (Strategy 패턴).
 *
 * <p>request 는 PENDING 으로 즉시 응답하고, 최종 결과는 웹훅 콜백으로 전달된다.
 * query 로 PG 측 상태를 폴링한다.
 */
public interface PaymentGateway {

    /** 결제를 요청한다. 거래 식별자와 PENDING 상태를 응답한다. */
    PaymentResponse request(PaymentRequest request);

    /** PG 측 현재 결제 상태를 조회한다. 처리 중이면 PENDING. */
    PaymentStatus query(String pgTransactionId);

    /** 결제를 환불한다. */
    RefundResponse refund(RefundRequest request);
}
