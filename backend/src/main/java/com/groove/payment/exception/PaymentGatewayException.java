package com.groove.payment.exception;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ExternalException;

/**
 * PG 연동 실패. HTTP 502.
 */
public class PaymentGatewayException extends ExternalException {

    public PaymentGatewayException(Throwable cause) {
        super(ErrorCode.PAYMENT_GATEWAY_FAILURE, ErrorCode.PAYMENT_GATEWAY_FAILURE.getDefaultMessage(), cause);
    }
}
