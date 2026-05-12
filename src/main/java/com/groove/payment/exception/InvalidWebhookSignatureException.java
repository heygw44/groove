package com.groove.payment.exception;

import com.groove.common.exception.BusinessException;
import com.groove.common.exception.ErrorCode;

/**
 * 결제 웹훅 서명 검증 실패. HTTP 401.
 *
 * <p>Mock 에서는 {@code X-Mock-Signature} 헤더(또는 {@code WebhookNotification.signature()})가 공유 시크릿
 * ({@code payment.mock.webhook-secret})과 일치하지 않을 때 발생한다. 실 PG 도입 시 PG 별 서명 검증 실패가
 * 이 예외로 정규화된다 (API.md §3.6 — {@code PAYMENT_WEBHOOK_INVALID_SIGNATURE}).
 */
public class InvalidWebhookSignatureException extends BusinessException {

    public InvalidWebhookSignatureException() {
        super(ErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE);
    }
}
