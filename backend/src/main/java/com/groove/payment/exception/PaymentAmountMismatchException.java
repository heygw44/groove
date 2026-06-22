package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;

/**
 * 서버에 저장된 결제 예정액과 토스 successUrl 리다이렉트로 전달된 amount 가 일치하지 않는 경우. HTTP 400.
 *
 * <p>클라이언트 금액 조작(위변조)을 confirm 호출 전에 차단하기 위한 방어선이다.
 */
public class PaymentAmountMismatchException extends DomainException {

    public PaymentAmountMismatchException(String orderNumber, long expected, long actual) {
        super(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                "결제 금액 불일치 order=" + orderNumber + ", 저장=" + expected + ", 요청=" + actual);
    }
}
