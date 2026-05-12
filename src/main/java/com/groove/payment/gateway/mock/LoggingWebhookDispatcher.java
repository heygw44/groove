package com.groove.payment.gateway.mock;

import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 웹훅 통보를 로그로만 남기는 기본 {@link WebhookDispatcher}.
 *
 * <p>실제 웹훅 수신 처리(서명 검증 · Payment/Order 상태 갱신 · 보상 트랜잭션)는 #W7-4 범위다.
 * 그 전까지는 발사된 통보가 어디로 가는지 추적만 할 수 있게 이 구현이 자리만 잡아 둔다.
 * #W7-4 에서 별도 {@code WebhookDispatcher} 빈이 등록되면 {@code @ConditionalOnMissingBean}
 * 에 의해 이 빈은 더 이상 생성되지 않는다 ({@link MockPaymentConfig} 참고).
 */
public class LoggingWebhookDispatcher implements WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LoggingWebhookDispatcher.class);

    @Override
    public void dispatch(WebhookNotification notification) {
        log.info("웹훅 수신 핸들러 미구현(#W7-4 예정) — 통보 폐기: pgTx={}, order={}, status={}, occurredAt={}",
                notification.pgTransactionId(), notification.orderNumber(),
                notification.status(), notification.occurredAt());
    }
}
