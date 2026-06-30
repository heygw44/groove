package com.groove.common.outbox;

import com.groove.common.transaction.CommonTransactionConfig;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 아웃박스 릴레이 스케줄러 — 미발행 이벤트를 주기적으로 컨슈머에 디스패치하고 발행 완료로 표시한다(at-least-once).
 *
 * 처리: published_at IS NULL 행을 id FIFO 로 .batch-size 만큼 조회 → 건별 OutboxEventHandler 디스패치 → 성공 시
 * markPublished(독립 트랜잭션). 실패는 건별 격리하고(다음 주기 재시도) 스케줄러 스레드 밖으로 예외를 흘리지 않는다.
 * 컨슈머 커밋 후 markPublished 가 실패하면 재디스패치되지만 컨슈머가 멱등이라 부수효과는 1회다.
 *
 * 영구 실패(poison) 격리: 핸들러 실패(예외/미등록) 시 attempt_count 를 증가시키고, 릴레이 조회는 attempt_count <
 * max-attempts 인 미발행 행만 대상으로 한다. 임계값을 채운 이벤트는 DLQ(격리)로 디스패치되지 않아 정상 슬롯을
 * 점유하지 않는다. markPublished 실패는 카운터를 증가시키지 않는다(멱등 재전달).
 *
 * 주기/초기 지연=groove.outbox.relay.{interval,initial-delay}, 처리 상한=.batch-size, 재시도 상한=.max-attempts.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final OutboxMetrics metrics;
    private final Limit batchLimit;
    private final int maxAttempts;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxRelayScheduler(OutboxEventRepository repository,
                                @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                Clock clock,
                                OutboxMetrics metrics,
                                List<OutboxEventHandler> handlers,
                                @Value("${groove.outbox.relay.batch-size:200}") int batchSize,
                                @Value("${groove.outbox.relay.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        this.metrics = metrics;
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.outbox.relay.batch-size 는 양수여야 합니다: " + batchSize);
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("groove.outbox.relay.max-attempts 는 양수여야 합니다: " + maxAttempts);
        }
        this.batchLimit = Limit.of(batchSize);
        this.maxAttempts = maxAttempts;
        // eventType 중복은 toMap 이 즉시 예외로 알린다.
        this.handlers = handlers.stream().collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
    }

    @Scheduled(
            fixedDelayString = "${groove.outbox.relay.interval:PT2S}",
            initialDelayString = "${groove.outbox.relay.initial-delay:PT5S}")
    @SchedulerLock(name = "outboxRelay",
            lockAtMostFor = "${groove.outbox.relay.lock-at-most-for:PT2M}",
            // 인터벌(기본 PT2S) 만큼 락을 잡아 다른 노드의 틱을 차단한다 — 0 이면 ~20ms 만에 풀려 같은 이벤트를 연속 재디스패치한다.
            lockAtLeastFor = "${groove.outbox.relay.lock-at-least-for:PT2S}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending =
                repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(maxAttempts, batchLimit);
        if (pending.isEmpty()) {
            return;
        }
        log.debug("아웃박스 릴레이 대상 {}건 (limit={})", pending.size(), batchLimit.max());
        for (OutboxEvent event : pending) {
            relayOne(event);
        }
    }

    private void relayOne(OutboxEvent event) {
        OutboxEventHandler handler = handlers.get(event.getEventType());
        if (handler == null) {
            // 핸들러 미등록도 영구 실패로 간주해 카운터를 올린다 — 모든 핸들러는 부팅 시 생성자로 주입되므로
            // 런타임 미등록은 구조적·영구 실패다. 격리하지 않으면 attempt_count 가 0 에 머물러 슬롯을 영구 점유한다.
            recordFailure(event, "핸들러 없음", null);
            return;
        }
        try {
            handler.handle(event);
        } catch (RuntimeException e) {
            recordFailure(event, "릴레이 실패", e);
            return;
        }
        // 컨슈머 커밋 후 markPublished 가 실패해도 카운터는 증가시키지 않는다 — 행이 미발행으로 남아 멱등 재전달된다.
        try {
            requiresNewTx.executeWithoutResult(status -> repository.markPublished(event.getId(), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("아웃박스 발행 표시 실패: eventType={}, id={} — 다음 주기에 재시도(멱등)", event.getEventType(), event.getId(), e);
        }
    }

    /**
     * 영구 실패(핸들러 예외 또는 미등록) 처리 — 재시도 카운터를 독립 트랜잭션으로 증가시키고 임계값 도달 시 DLQ
     * 격리를 로그로 알린다. 카운터 증가 자체가 실패해도(DB 일시 장애 등) 예외를 삼켜 같은 배치의 나머지 이벤트
     * 처리를 막지 않는다(건별 격리).
     */
    private void recordFailure(OutboxEvent event, String reason, RuntimeException cause) {
        try {
            requiresNewTx.executeWithoutResult(status -> repository.incrementAttemptCount(event.getId()));
        } catch (RuntimeException e) {
            log.warn("아웃박스 재시도 카운터 증가 실패: eventType={}, id={} — 다음 주기에 재시도", event.getEventType(), event.getId(), e);
            return;
        }
        // 단일 스케줄러 인스턴스 기준 조회 스냅샷 + 1 이 증가 후 권위 값이다(멀티 인스턴스에선 로그상 근사일 수 있음).
        int attempts = event.getAttemptCount() + 1;
        if (attempts >= maxAttempts) {
            log.error("아웃박스 릴레이 DLQ 격리: eventType={}, id={}, attempts={}/{}, 사유={} — 재시도 중단(수동 조치 필요)",
                    event.getEventType(), event.getId(), attempts, maxAttempts, reason, cause);
            // 릴레이 조회가 격리 행을 제외하므로 이 호출은 이벤트당 정확히 1회(상한 도달 전이) 실행된다 → 메트릭 중복 없음.
            // 에러 로그 뒤에 두고 실패를 삼켜, 메트릭 장애가 격리 로그 누락이나 배치(건별 격리)를 깨지 않게 한다.
            try {
                metrics.recordQuarantined(event.getEventType());
            } catch (RuntimeException e) {
                log.warn("DLQ 격리 메트릭 기록 실패: eventType={}, id={}", event.getEventType(), event.getId(), e);
            }
        } else {
            log.warn("아웃박스 릴레이 실패: eventType={}, id={}, attempts={}/{}, 사유={} — 다음 주기에 재시도",
                    event.getEventType(), event.getId(), attempts, maxAttempts, reason, cause);
        }
    }
}
