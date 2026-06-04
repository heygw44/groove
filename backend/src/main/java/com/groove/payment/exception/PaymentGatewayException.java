package com.groove.payment.exception;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ExternalException;

/**
 * PG 연동 실패. HTTP 502.
 *
 * <p>v1 Mock PG({@code MockPaymentGateway.request()})는 예외를 던지지 않지만, 실 PG 도입 대비로
 * {@code PaymentService} 가 게이트웨이 호출을 감싸 이 예외로 정규화한다 (API.md §3.6 — {@code
 * PAYMENT_GATEWAY_FAILURE}).
 */
public class PaymentGatewayException extends ExternalException {

    public PaymentGatewayException(Throwable cause) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILURE, ErrorCode.PAYMENT_GATEWAY_FAILURE.getDefaultMessage(), cause);
    }
}
