package com.groove.payment.gateway.mock;

import com.groove.payment.domain.PaymentStatus;
import com.groove.payment.gateway.ConfirmResponse;
import com.groove.payment.gateway.PaymentGateway;
import com.groove.payment.gateway.PaymentMockProperties;
import com.groove.payment.gateway.PaymentRequest;
import com.groove.payment.gateway.PaymentResponse;
import com.groove.payment.gateway.RefundRequest;
import com.groove.payment.gateway.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 결제 라이프사이클을 재현하는 Mock 게이트웨이.
 *
 * <ul>
 *   <li>request() — 처리 지연 후 거래 식별자를 발급하고 성공률로 최종 결과(PAID/FAILED)를 결정한 뒤
 *       웹훅 발사 시각을 정해 (auto-webhook 이 true 면) MockWebhookSimulator 에 예약하고 PENDING 으로 응답.</li>
 *   <li>confirm() — 토스 동기 승인 모델 흉내. paymentKey 거래를 즉시 PAID 로 기록하고 PAID 를 응답.</li>
 *   <li>query() — 웹훅 발사 시각 전이면 PENDING, 이후면 결정된 최종 상태(또는 환불 시 REFUNDED).</li>
 *   <li>refund() — 같은 idempotencyKey 는 첫 응답을 그대로 재반환하고, 다른 키는 REFUNDED 응답을 새로 만든다.</li>
 * </ul>
 *
 * <p>거래 상태는 프로세스 메모리에만 보관하며, MAX_TRACKED_TRANSACTIONS 도달 시 오래된 항목을 정리한다.
 */
@Component
@Profile({"local", "test", "docker"})
public class MockPaymentGateway implements PaymentGateway {

    /** provider 식별자. */
    public static final String PROVIDER = "MOCK";

    /** 추적 거래 수 상한 — 초과 시 PRUNE_AGE 보다 오래된 항목을 정리한다. */
    static final int MAX_TRACKED_TRANSACTIONS = 10_000;
    /** 발사 후 이 시간이 지난 거래는 정리 대상으로 삼는다. */
    static final Duration PRUNE_AGE = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final PaymentMockProperties properties;
    private final MockWebhookSimulator webhookSimulator;
    private final Clock clock;
    private final boolean autoWebhook;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    /** idempotencyKey → 첫 환불 응답 캐시. 같은 키 재호출 시 첫 응답을 그대로 반환한다. */
    private final Map<String, RefundResponse> refundCache = new ConcurrentHashMap<>();
    /** 캐시 미스로 실제 환불 처리를 수행한 횟수. */
    private final AtomicInteger refundCallCount = new AtomicInteger();

    public MockPaymentGateway(PaymentMockProperties properties, MockWebhookSimulator webhookSimulator, Clock clock,
                              @Value("${payment.mock.auto-webhook:true}") boolean autoWebhook) {
        this.properties = Objects.requireNonNull(properties);
        this.webhookSimulator = Objects.requireNonNull(webhookSimulator);
        this.clock = Objects.requireNonNull(clock);
        this.autoWebhook = autoWebhook;
    }

    @Override
    public PaymentResponse request(PaymentRequest request) {
        Objects.requireNonNull(request, "request");
        simulateProcessingLatency();

        Instant now = clock.instant();
        pruneStaleIfFull(now);

        String pgTransactionId = "mock-tx-" + UUID.randomUUID();
        PaymentStatus result = rollOutcome();
        Instant fireAt = now.plus(randomDuration(properties.webhookDelayMin(), properties.webhookDelayMax()));

        transactions.put(pgTransactionId, new Transaction(result, fireAt));
        if (autoWebhook) {
            webhookSimulator.scheduleCallback(pgTransactionId, request.orderNumber(), result, fireAt);
        }

        log.info("Mock 결제 접수: pgTx={}, order={}, amount={}, 예정결과={}, 발사시각={}, autoWebhook={}",
                pgTransactionId, request.orderNumber(), request.amount(), result, fireAt, autoWebhook);
        return new PaymentResponse(pgTransactionId, PaymentStatus.PENDING, PROVIDER);
    }

    @Override
    public ConfirmResponse confirm(String paymentKey, String orderId, long amount) {
        // 상태 변경(transactions.put) 전에 입력을 검증한다 — blank 면 거래가 남지 않도록 ConfirmResponse 생성 전 거른다.
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey 는 비어 있을 수 없습니다");
        }
        simulateProcessingLatency();

        Instant now = clock.instant();
        pruneStaleIfFull(now);

        // confirm 은 동기 확정 — 즉시 PAID 거래로 기록(fireAt=now 라 이후 query 가 바로 PAID 를 반환).
        transactions.put(paymentKey, new Transaction(PaymentStatus.PAID, now));

        log.info("Mock 결제 승인(confirm): paymentKey={}, order={}, amount={} → PAID", paymentKey, orderId, amount);
        // Mock 은 실제 결제수단을 모른다 — method 는 null 로 두어 호출부가 잠정 method 를 유지하게 한다.
        return new ConfirmResponse(paymentKey, PaymentStatus.PAID, null);
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
        if (clock.instant().isBefore(tx.fireAt())) {
            return PaymentStatus.PENDING;
        }
        return tx.currentStatus();
    }

    @Override
    public RefundResponse refund(RefundRequest request) {
        Objects.requireNonNull(request, "request");

        // 캐시 정리는 computeIfAbsent 진입 전에 끝낸다.
        pruneRefundCacheIfFull();

        // 같은 idempotencyKey 는 첫 응답을 그대로 재사용하고, 캐시 미스에서만 REFUNDED 전이와 지연 시뮬레이션을 수행한다.
        return refundCache.computeIfAbsent(request.idempotencyKey(), key -> {
            simulateProcessingLatency();

            // 환불을 즉시 성공 처리하고, 알려진 거래면 상태를 REFUNDED 로 갱신한다.
            transactions.computeIfPresent(request.pgTransactionId(),
                    (id, tx) -> new Transaction(PaymentStatus.REFUNDED, tx.fireAt()));
            refundCallCount.incrementAndGet();

            log.info("Mock 환불 처리: key={}, pgTx={}, amount={}", key, request.pgTransactionId(), request.amount());
            return new RefundResponse(request.pgTransactionId(), PaymentStatus.REFUNDED, clock.instant());
        });
    }

    /** 실제로 환불을 처리한(캐시 미스) 횟수. 멱등 키 재호출은 카운트되지 않는다. */
    public int refundCallCount() {
        return refundCallCount.get();
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

    /** 환불 캐시가 상한에 도달하면 첫 항목부터 절반을 정리한다. */
    private void pruneRefundCacheIfFull() {
        if (refundCache.size() < MAX_TRACKED_TRANSACTIONS) {
            return;
        }
        int targetRemovals = refundCache.size() - MAX_TRACKED_TRANSACTIONS / 2;
        refundCache.keySet().stream()
                .limit(targetRemovals)
                .toList()
                .forEach(refundCache::remove);
    }

    private void pruneStaleIfFull(Instant now) {
        if (transactions.size() < MAX_TRACKED_TRANSACTIONS) {
            return;
        }
        Instant cutoff = now.minus(PRUNE_AGE);
        transactions.entrySet().removeIf(e -> e.getValue().fireAt().isBefore(cutoff));

        // stale 항목만으로 상한을 못 맞추면 발사 시각이 오래된 순으로 (size - MAX + 1) 만큼 강제 축출한다.
        int overflow = transactions.size() - MAX_TRACKED_TRANSACTIONS + 1;
        if (overflow > 0) {
            transactions.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getValue().fireAt()))
                    .limit(overflow)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(transactions::remove);
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
     * <p>currentStatus: 현재 상태. fireAt: 웹훅 콜백 발사 시각(이 시각 이전 조회는 PENDING).
     */
    private record Transaction(PaymentStatus currentStatus, Instant fireAt) {
    }
}
