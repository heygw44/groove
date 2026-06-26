package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 저장된 결제 예정액과 successUrl 리다이렉트 amount 불일치. HTTP 400.
 * 클라이언트 금액 위변조를 confirm 호출 전에 차단하는 방어선.
 */
public class PaymentAmountMismatchException extends DomainException {

    public PaymentAmountMismatchException(String orderNumber, long expected, long actual) {
        super(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                "결제 금액 불일치 order=" + orderNumber + ", 저장=" + expected + ", 요청=" + actual);
    }
}
