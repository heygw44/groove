package com.groove.payment.exception;

import com.groove.common.exception.DomainException;
import com.groove.common.exception.ErrorCode;
import com.groove.payment.domain.PaymentStatus;

/**
 * 환불할 수 없는 결제 상태에 환불을 요청한 경우. HTTP 409.
 *
 * <p>{@code PAID} 결제만 환불 가능하다 — PENDING(아직 미확정)·FAILED(확정 실패)에 환불을 시도하면 발생한다.
 * 이미 {@code REFUNDED} 인 결제는 예외가 아니라 멱등 응답으로 처리한다(중복 환불 요청 무해, 이슈 #69 DoD).
 */
public class PaymentNotRefundableException extends DomainException {

    public PaymentNotRefundableException(PaymentStatus current) {
        super(ErrorCode.PAYMENT_NOT_REFUNDABLE, "환불할 수 없는 결제 상태입니다: " + current);
    }
}
