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
import java.time.Duration;
import java.util.Objects;

/**
 * 실 PG 의 비동기 웹훅 콜백을 흉내 내는 발사기.
 *
 * <p>{@link MockPaymentGateway#request} 호출 후 설정된 지연({@code payment.mock.webhook-delay-*})
 * 만큼 뒤에 {@link WebhookDispatcher} 로 결제 결과 통보를 전달한다. 스케줄링은 전용
 * {@link TaskScheduler}({@code paymentTaskScheduler}) 위에서 일회성 작업으로 수행된다.
 *
 * <p>{@code @Profile} 로 Mock 구성에 한정되며, 실 PG 프로파일에서는 로드되지 않는다.
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

    /**
     * {@code delay} 후 결제 결과 웹훅 콜백을 발사하도록 예약한다.
     *
     * @param pgTransactionId PG 거래 식별자
     * @param orderNumber     주문 식별자
     * @param result          최종 결제 결과 ({@link PaymentStatus#PAID} 또는 {@link PaymentStatus#FAILED})
     * @param delay           발사까지의 지연
     */
    public void scheduleCallback(String pgTransactionId, String orderNumber, PaymentStatus result, Duration delay) {
        Objects.requireNonNull(delay, "delay");
        scheduler.schedule(() -> fire(pgTransactionId, orderNumber, result), clock.instant().plus(delay));
        log.debug("웹훅 콜백 예약: pgTx={}, order={}, result={}, delay={}", pgTransactionId, orderNumber, result, delay);
    }

    private void fire(String pgTransactionId, String orderNumber, PaymentStatus result) {
        WebhookNotification notification = new WebhookNotification(
                pgTransactionId, orderNumber, result, clock.instant(), properties.webhookSecret());
        try {
            dispatcher.dispatch(notification);
        } catch (RuntimeException e) {
            // 발사기는 통보 전달 실패를 삼키지 않고 로깅만 한다 — 수신 측 정합성 복구는 폴링 스케줄러(#W7-4) 책임.
            log.warn("웹훅 콜백 전달 실패: pgTx={}, order={}", pgTransactionId, orderNumber, e);
        }
    }
}
