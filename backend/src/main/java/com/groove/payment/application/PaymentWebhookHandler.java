package com.groove.payment.application;

import com.groove.common.idempotency.IdempotencyService;
import com.groove.payment.api.dto.PaymentCallbackResult;
import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
import com.groove.payment.gateway.WebhookSignatureVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 인프로세스 웹훅 콜백 수신기. 서명을 검증하고 PaymentCallbackService 로 처리한다.
 * pgTransactionId 기반 키로 멱등 처리한다.
 */
@Component
@Profile({"local", "dev", "test", "docker"})
public class PaymentWebhookHandler implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookHandler.class);

    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentCallbackService callbackService;
    private final IdempotencyService idempotencyService;

    public PaymentWebhookHandler(WebhookSignatureVerifier signatureVerifier,
                                 PaymentCallbackService callbackService,
                                 IdempotencyService idempotencyService) {
        this.signatureVerifier = signatureVerifier;
        this.callbackService = callbackService;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void dispatch(WebhookNotification notification) {
        signatureVerifier.verify(notification.signature());
        PaymentCallbackResult result = idempotencyService.execute(
                PaymentCallbackService.idempotencyKeyFor(notification.pgTransactionId()),
                PaymentCallbackResult.class,
                () -> callbackService.applyResult(notification.pgTransactionId(), notification.status(), null));
        log.debug("인프로세스 웹훅 처리: pgTx={}, outcome={}", notification.pgTransactionId(), result.outcome());
    }
}
