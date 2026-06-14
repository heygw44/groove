package com.groove.payment.gateway;

import com.groove.payment.exception.PaymentGatewayException;

/**
 * PG 환불 호출의 공통 예외 정규화 헬퍼.
 *
 * <p>{@code AdminOrderService.refund}(발송 전 전액 환불)와 {@code ClaimService.completeRefund}(반품 부분 환불)가
 * 동일하게 {@link PaymentGateway#refund} 의 {@link RuntimeException} 을 {@link PaymentGatewayException}(502)로
 * 감싸므로, 그 래핑을 한 곳에 모은다. 멱등 키는 호출 측이 {@link RefundRequest} 에 결정적으로 담아 전달한다(#72).
 */
public final class GatewayRefunds {

    private GatewayRefunds() {
    }

    public static RefundResponse refund(PaymentGateway gateway, RefundRequest request) {
        try {
            return gateway.refund(request);
        } catch (RuntimeException gatewayFailure) {
            throw new PaymentGatewayException(gatewayFailure);
        }
    }
}
