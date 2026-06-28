package com.groove.payment.gateway;

/**
 * PG 연동 추상화(Strategy). 두 승인 모델을 함께 수용한다.
 * 비동기(request): PENDING 즉시 응답, 최종 결과는 웹훅 콜백, query 로 폴링(Mock).
 * 동기(confirm): 위젯이 발급한 paymentKey 로 서버가 즉시 승인하고 확정 상태를 응답으로 받는다(토스).
 */
public interface PaymentGateway {

    /** 결제를 요청한다. 거래 식별자와 PENDING 상태를 응답한다. */
    PaymentResponse request(PaymentRequest request);

    /**
     * 결제를 동기 승인한다(토스 위젯 모델). paymentKey/orderId/amount 로 승인하고 확정 상태를 즉시 반환한다.
     * 대개 PAID 이지만 가상계좌 등은 PENDING 으로 응답될 수 있어 호출부는 PENDING 도 처리해야 한다(Mock 은 항상 PAID).
     */
    ConfirmResponse confirm(String paymentKey, String orderId, long amount);

    /**
     * PG 측 현재 상태 + (보고됐다면) 권위 정산금액 조회. 처리 중이면 PENDING.
     * 정산금액은 PAID 정산 전 저장 금액과 대조해 위변조를 차단한다. 미보고면 null 이고 호출부는 검증을 생략한다.
     */
    GatewayQuery query(String pgTransactionId);

    /** 결제를 환불한다. */
    RefundResponse refund(RefundRequest request);
}
