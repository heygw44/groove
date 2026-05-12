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
 * 실 PG 의 비동기 웹훅 콜백을 흉내 내는 발사기.
 *
 * <p>{@link MockPaymentGateway#request} 가 정한 발사 시각({@code fireAt})에 {@link WebhookDispatcher}
 * 로 결제 결과 통보를 전달한다. 발사 타이밍은 게이트웨이가 단일 기준({@code Transaction.readyAt} 과
 * 동일한 {@link Instant})으로 소유하며, 본 발사기는 그 시각에 일회성 작업을 예약하기만 한다.
 * 스케줄링은 전용 {@link TaskScheduler}({@code paymentTaskScheduler}) 위에서 수행된다.
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
     * {@code fireAt} 시각에 결제 결과 웹훅 콜백을 발사하도록 예약한다.
     *
     * @param pgTransactionId PG 거래 식별자
     * @param orderNumber     주문 식별자
     * @param result          최종 결제 결과 ({@link PaymentStatus#PAID} 또는 {@link PaymentStatus#FAILED})
     * @param fireAt          콜백 발사 시각 (게이트웨이가 결정)
     */
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
            // 발사기는 통보 전달 실패를 삼키지 않고 로깅만 한다 — 수신 측 정합성 복구는 폴링 스케줄러(#W7-4) 책임.
            log.warn("웹훅 콜백 전달 실패: pgTx={}, order={}", pgTransactionId, orderNumber, e);
        }
    }
}
