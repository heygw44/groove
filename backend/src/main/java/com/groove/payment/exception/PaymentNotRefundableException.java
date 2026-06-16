package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.payment.domain.PaymentStatus;

/**
 * 환불할 수 없는 결제 상태에 환불을 요청한 경우. HTTP 409.
 */
public class PaymentNotRefundableException extends DomainException {

    public PaymentNotRefundableException(PaymentStatus current) {
        super(ErrorCode.PAYMENT_NOT_REFUNDABLE, "환불할 수 없는 결제 상태입니다: " + current);
    }
}
