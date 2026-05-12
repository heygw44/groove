package com.groove.payment.gateway.mock;

import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 실 PG 없이 결제 라이프사이클을 재현하는 Mock 게이트웨이 (ARCHITECTURE.md §7).
 *
 * <p>동작:
 * <ul>
 *   <li>{@code request()} — 처리 지연({@code payment.mock.delay-*})을 흉내 낸 뒤 거래 식별자를 발급하고,
 *       성공률({@code payment.mock.success-rate})로 최종 결과(PAID/FAILED)를 미리 결정한 다음
 *       {@link MockWebhookSimulator} 로 웹훅 콜백을 예약하고 {@link PaymentStatus#PENDING} 으로 즉시 응답.</li>
 *   <li>{@code query()} — 웹훅 발사 예정 시각 전이면 PENDING, 이후면 결정된 최종 상태(또는 환불 시 REFUNDED).</li>
 *   <li>{@code refund()} — 항상 성공 처리하고 {@link PaymentStatus#REFUNDED} 응답.</li>
 * </ul>
 *
 * <p>거래 상태는 프로세스 메모리(JVM 재시작 시 소실)에만 보관한다 — Mock 시연 용도로 충분하다.
 * {@code @Profile} 로 격리되어 실 PG 프로파일에서는 로드되지 않는다.
 */
@Component
@Profile({"local", "dev", "test", "docker"})
public class MockPaymentGateway implements PaymentGateway {

    /** {@link PaymentResponse#provider()} 식별자. */
    public static final String PROVIDER = "MOCK";

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final PaymentMockProperties properties;
    private final MockWebhookSimulator webhookSimulator;
    private final Clock clock;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();

    public MockPaymentGateway(PaymentMockProperties properties, MockWebhookSimulator webhookSimulator, Clock clock) {
        this.properties = Objects.requireNonNull(properties);
        this.webhookSimulator = Objects.requireNonNull(webhookSimulator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PaymentResponse request(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        simulateProcessingLatency();

        String pgTransactionId = "mock-tx-" + UUID.randomUUID();
        PaymentStatus result = rollOutcome();
        Duration webhookDelay = randomDuration(properties.webhookDelayMin(), properties.webhookDelayMax());
        Instant readyAt = clock.instant().plus(webhookDelay);

        transactions.put(pgTransactionId, new Transaction(result, readyAt));
        webhookSimulator.scheduleCallback(pgTransactionId, request.orderNumber(), result, webhookDelay);

        log.info("Mock 결제 접수: pgTx={}, order={}, amount={}, 예정결과={}, 웹훅지연={}",
                pgTransactionId, request.orderNumber(), request.amount(), result, webhookDelay);
        return new PaymentResponse(pgTransactionId, PaymentStatus.PENDING, PROVIDER);
    }

    @Override
    public PaymentStatus query(String pgTransactionId) {
        Objects.requireNonNull(pgTransactionId, "pgTransactionId");
        simulateProcessingLatency();

        Transaction tx = transactions.get(pgTransactionId);
        if (tx == null) {
            log.warn("Mock 결제 조회: 알 수 없는 거래 pgTx={} — PENDING 으로 응답", pgTransactionId);
            return PaymentStatus.PENDING;
        }
        if (clock.instant().isBefore(tx.readyAt())) {
            return PaymentStatus.PENDING;
        }
        return tx.currentStatus();
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        Objects.requireNonNull(request, "request");
        simulateProcessingLatency();

        // Mock 은 환불을 항상 즉시 성공 처리한다. 알려진 거래면 상태를 REFUNDED 로 갱신해 이후 query() 와 정합을 맞춘다.
        transactions.computeIfPresent(request.pgTransactionId(),
                (id, tx) -> new Transaction(PaymentStatus.REFUNDED, tx.readyAt()));

        log.info("Mock 환불 처리: pgTx={}, amount={}", request.pgTransactionId(), request.amount());
        return new RefundResponse(request.pgTransactionId(), PaymentStatus.REFUNDED, clock.instant());
    }

    private PaymentStatus rollOutcome() {
        return ThreadLocalRandom.current().nextDouble() < properties.successRate()
                ? PaymentStatus.PAID
                : PaymentStatus.FAILED;
    }

    private void simulateProcessingLatency() {
        Duration delay = randomDuration(properties.delayMin(), properties.delayMax());
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Duration randomDuration(Duration min, Duration max) {
        long minMs = min.toMillis();
        long maxMs = max.toMillis();
        if (minMs >= maxMs) {
            return Duration.ofMillis(minMs);
        }
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(minMs, maxMs + 1));
    }

    /**
     * 거래 상태 스냅샷.
     *
     * @param currentStatus 현재 상태 (요청 시점에 결정된 최종 결과, 또는 환불 후 REFUNDED)
     * @param readyAt       이 시각 이전 조회는 PENDING 으로 응답 (웹훅 발사 예정 시각)
     */
    private record Transaction(PaymentStatus currentStatus, Instant readyAt) {
    }
}
