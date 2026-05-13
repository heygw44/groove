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
 * 실 PG 없이 결제 라이프사이클을 재현하는 Mock 게이트웨이 (ARCHITECTURE.md §7).
 *
 * <p>동작:
 * <ul>
 *   <li>{@code request()} — 처리 지연({@code payment.mock.delay-*})을 흉내 낸 뒤 거래 식별자를 발급하고,
 *       성공률({@code payment.mock.success-rate})로 최종 결과(PAID/FAILED)를 미리 결정한 다음
 *       웹훅 발사 시각({@code now + webhook-delay})을 정해 ({@code payment.mock.auto-webhook} 이 true 면)
 *       {@link MockWebhookSimulator} 에 예약하고 {@link PaymentStatus#PENDING} 으로 즉시 응답.
 *       {@code auto-webhook=false} 면 거래는 기록하되 자동 웹훅을 발사하지 않는다 — 통합 테스트에서 HTTP
 *       웹훅 엔드포인트/폴링을 직접 호출해 결정적으로 검증하기 위함이다.</li>
 *   <li>{@code query()} — 웹훅 발사 예정 시각 전이면 PENDING, 이후면 결정된 최종 상태(또는 환불 시 REFUNDED).</li>
 *   <li>{@code refund()} — 같은 {@code idempotencyKey} 에 대해 첫 응답을 그대로 재반환하여 PG 실호출
 *       (transactions 의 REFUNDED 전이 포함) 이 정확히 1회만 일어나도록 보장한다 (#72, Stripe
 *       {@code Idempotency-Key} 와 동일 계약). 다른 키는 별개 호출로 처리하고 {@link PaymentStatus#REFUNDED}
 *       응답을 새로 만든다.</li>
 * </ul>
 *
 * <p>거래 상태는 프로세스 메모리(JVM 재시작 시 소실)에만 보관한다 — Mock 시연 용도로 충분하다.
 * 무한 증가를 막기 위해 {@link #MAX_TRACKED_TRANSACTIONS} 도달 시 발사된 지 충분히 지난 항목을
 * 정리한다(재조회 가능성이 사실상 없는 시점). 환불 캐시({@link #refundCache}) 도 동일 상한 정책으로
 * 오래된 항목부터 축출한다. {@code @Profile} 로 격리되어 실 PG 프로파일에서는 로드되지 않는다.
 */
@Component
@Profile({"local", "dev", "test", "docker"})
public class MockPaymentGateway implements PaymentGateway {

    /** {@link PaymentResponse#provider()} 식별자. */
    public static final String PROVIDER = "MOCK";

    /** 추적 거래 수 상한 — 초과 시 {@link #PRUNE_AGE} 보다 오래된 항목을 정리한다. */
    static final int MAX_TRACKED_TRANSACTIONS = 10_000;
    /** 발사 후 이 시간이 지난 거래는 재조회 가능성이 없다고 보고 정리 대상으로 삼는다. */
    static final Duration PRUNE_AGE = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final PaymentMockProperties properties;
    private final MockWebhookSimulator webhookSimulator;
    private final Clock clock;
    private final boolean autoWebhook;
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    /** {@code idempotencyKey → 첫 환불 응답} 캐시 (#72). 같은 키 재호출 시 첫 응답을 그대로 반환한다. */
    private final Map<String, RefundResponse> refundCache = new ConcurrentHashMap<>();
    /** 캐시 미스로 실제 환불 처리를 수행한 횟수 — 테스트의 멱등성 검증용. */
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

        // 같은 idempotencyKey 는 첫 응답을 그대로 재사용한다 (#72) — 보상 트랜잭션 부분 실패 후 재시도가
        // 와도 PG 측 환불은 1회로 정상화된다. 캐시 미스인 경우에만 transactions 의 REFUNDED 전이와
        // 처리 지연 시뮬레이션을 수행한다.
        return refundCache.computeIfAbsent(request.idempotencyKey(), key -> {
            simulateProcessingLatency();
            pruneRefundCacheIfFull();

            // Mock 은 환불을 항상 즉시 성공 처리한다. 알려진 거래면 상태를 REFUNDED 로 갱신해 이후 query() 와 정합을 맞춘다.
            transactions.computeIfPresent(request.pgTransactionId(),
                    (id, tx) -> new Transaction(PaymentStatus.REFUNDED, tx.fireAt()));
            refundCallCount.incrementAndGet();

            log.info("Mock 환불 처리: key={}, pgTx={}, amount={}", key, request.pgTransactionId(), request.amount());
            return new RefundResponse(request.pgTransactionId(), PaymentStatus.REFUNDED, clock.instant());
        });
    }

    /**
     * 테스트 전용 — 실제로 환불을 처리한(캐시 미스) 횟수. 멱등 키 재호출은 카운트되지 않는다.
     * 운영 로직에선 호출하지 않는다.
     */
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

    /**
     * 환불 캐시가 상한에 도달하면 첫 항목부터(=가장 오래된 시점 — 삽입 순으로 들어옴) 절반을 정리한다.
     * {@code ConcurrentHashMap} 은 강한 삽입 순서를 보장하진 않지만 (entry set iterator 가 약한 일관성),
     * Mock 데모 용도라 정확한 LRU 가 아니어도 상한만 지키면 충분하다.
     */
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

        // stale 항목만으로 상한을 못 맞추는 경우(신선한 거래가 계속 유입) — 발사 시각이 오래된 순으로 강제 축출해
        // 상한을 실질적으로 보장한다. 다음 put 이 1건 추가하므로 (size - MAX + 1) 만큼 비워 두면 충분하다.
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
     * @param currentStatus 현재 상태 (요청 시점에 결정된 최종 결과, 또는 환불 후 REFUNDED)
     * @param fireAt        웹훅 콜백 발사 시각 — 이 시각 이전 조회는 PENDING 으로 응답한다
     */
    private record Transaction(PaymentStatus currentStatus, Instant fireAt) {
    }
}
