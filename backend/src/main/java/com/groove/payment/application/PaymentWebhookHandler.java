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
 * 인프로세스 웹훅 콜백 수신기 (#W7-4) — {@code MockWebhookSimulator} 가 비동기로 발사하는
 * {@link WebhookNotification} 을 받아 서명을 검증하고 {@link PaymentCallbackService} 로 처리한다.
 * #W7-1 의 {@code LoggingWebhookDispatcher}(자리만 잡던 빈)를 대체한다.
 *
 * <p>HTTP {@code POST /api/v1/payments/webhook}({@code PaymentWebhookController})·폴링 스케줄러와 동일하게
 * {@code pgTransactionId} 기반 키로 {@link IdempotencyService} 를 통해 멱등 처리한다 — 세 경로가 같은 키를
 * 공유하므로 어느 조합으로 중복 수신해도 상태 전이는 1회다. 디스패처는 비트랜잭션 — {@code applyResult} 가
 * 자기 트랜잭션을 커밋한 뒤 멱등성 마커가 갱신되도록 ({@code IdempotencyService} 호출 규약).
 *
 * <p>{@code @Profile} 로 Mock 구성({@code MockWebhookSimulator} 존재 프로파일)에 한정한다.
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
