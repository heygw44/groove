package com.groove.payment.gateway.mock;

import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.WebhookDispatcher;
import com.groove.payment.gateway.WebhookNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 비동기 웹훅 콜백을 흉내 내는 발사기.
 *
 * <p>게이트웨이가 정한 발사 시각(fireAt)에 전용 TaskScheduler(paymentTaskScheduler)
 * 위에서 일회성 작업을 예약해 WebhookDispatcher 로 결제 결과 통보를 전달한다.
 */
@Component
@Profile({"local", "dev", "test", "docker"})
public class MockWebhookSimulator {

    private static final Logger log = LoggerFactory.getLogger(MockWebhookSimulator.class);

    private final TaskScheduler scheduler;
    private final WebhookDispatcher dispatcher;
    private final PaymentMockProperties properties;
    private final Clock clock;

    public MockWebhookSimulator(
            @Qualifier("paymentTaskScheduler") TaskScheduler scheduler,
            WebhookDispatcher dispatcher,
            PaymentMockProperties properties,
            Clock clock) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /** fireAt 시각에 결제 결과 웹훅 콜백을 발사하도록 예약한다. */
    public void scheduleCallback(String pgTransactionId, String orderNumber, PaymentStatus result, Instant fireAt) {
        Objects.requireNonNull(fireAt, "fireAt");
        scheduler.schedule(() -> fire(pgTransactionId, orderNumber, result), fireAt);
        log.debug("웹훅 콜백 예약: pgTx={}, order={}, result={}, fireAt={}", pgTransactionId, orderNumber, result, fireAt);
    }

    private void fire(String pgTransactionId, String orderNumber, PaymentStatus result) {
        WebhookNotification notification = new WebhookNotification(
                pgTransactionId, orderNumber, result, clock.instant(), properties.webhookSecret());
        try {
            dispatcher.dispatch(notification);
        } catch (RuntimeException e) {
            // 통보 전달 실패는 로깅만 한다.
            log.warn("웹훅 콜백 전달 실패: pgTx={}, order={}", pgTransactionId, orderNumber, e);
        }
    }
}
