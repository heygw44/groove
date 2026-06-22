package com.groove.payment.gateway;

import com.groove.payment.domain.PaymentStatus;

/**
 * PG 연동 추상화 (Strategy 패턴).
 *
 * <p>두 가지 승인 모델을 함께 수용한다.
 * <ul>
 *   <li><b>비동기(request):</b> request 는 PENDING 으로 즉시 응답하고 최종 결과는 웹훅 콜백으로 전달된다.
 *       query 로 PG 측 상태를 폴링한다. (Mock 게이트웨이가 이 모델을 따른다.)</li>
 *   <li><b>동기(confirm):</b> 클라이언트 위젯이 발급한 paymentKey 로 서버가 결제를 즉시 승인하고
 *       확정 상태를 응답으로 받는다. (토스페이먼츠가 이 모델을 따른다.)</li>
 * </ul>
 */
public interface PaymentGateway {

    /** 결제를 요청한다. 거래 식별자와 PENDING 상태를 응답한다. */
    PaymentResponse request(PaymentRequest request);

    /**
     * 결제를 동기 승인한다(토스 위젯 결제 모델). paymentKey/orderId/amount 로 승인하고 확정 상태를 즉시 반환한다.
     *
     * <p>{@code request → PENDING → 웹훅}(비동기) 모델과 달리 응답 시점에 결과가 정해진다. 대개 PAID 이지만,
     * 가상계좌 등 입금 전 수단은 PENDING 으로 응답될 수 있으므로 호출부는 PENDING 도 처리해야 한다.
     * (MockPaymentGateway 는 단순화를 위해 항상 PAID 를 반환한다.)
     */
    ConfirmResponse confirm(String paymentKey, String orderId, long amount);

    /** PG 측 현재 결제 상태를 조회한다. 처리 중이면 PENDING. */
    PaymentStatus query(String pgTransactionId);

    /** 결제를 환불한다. */
    RefundResponse refund(RefundRequest request);
}
