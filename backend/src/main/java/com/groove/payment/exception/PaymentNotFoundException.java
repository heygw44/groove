package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 결제를 찾을 수 없는 경우. HTTP 404.
 */
public class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException() {
        super(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
