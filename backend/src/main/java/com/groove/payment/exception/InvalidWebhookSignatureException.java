package com.groove.payment.exception;

import com.groove.common.exception.BusinessException;
import com.groove.common.exception.ErrorCode;

/**
 * 결제 웹훅 서명 검증 실패. HTTP 401.
 */
public class InvalidWebhookSignatureException extends BusinessException {

    public InvalidWebhookSignatureException() {
        super(ErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE);
    }
}
