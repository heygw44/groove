package com.groove.payment.gateway;

import com.groove.payment.exception.PaymentGatewayException;

/** PG 환불 호출의 공통 예외 정규화 헬퍼. refund 의 RuntimeException 을 PaymentGatewayException(502)로 감싼다. */
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
