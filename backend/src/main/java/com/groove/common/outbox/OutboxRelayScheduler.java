package com.groove.common.outbox;

import com.groove.common.transaction.CommonTransactionConfig;
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
 * 아웃박스 릴레이 스케줄러 — 미발행 이벤트를 주기적으로 컨슈머에 디스패치하고 발행 완료로 표시한다
 * (at-least-once).
 *
 * <p>처리: published_at IS NULL 행을 id FIFO 로 .batch-size 만큼 조회 → 건별로 OutboxEventHandler 디스패치 →
 * 성공 시 markPublished(독립 트랜잭션). 한 건의 실패는 건별로 격리하고(다음 주기에 재시도), 스케줄러 스레드 밖으로
 * 예외를 흘리지 않는다.
 *
 * <p>컨슈머가 커밋한 뒤 markPublished 가 실패하면 행이 미발행으로 남아 재디스패치되지만, 컨슈머가 멱등이라
 * 부수효과는 1회다.
 *
 * <p>영구 실패(poison) 격리: 핸들러 실패 시 attempt_count 를 증가시키고, 릴레이 조회는 attempt_count <
 * max-attempts 인 미발행 행만 대상으로 한다. 임계값을 채운 이벤트는 DLQ(격리)로 더 이상 디스패치되지 않아
 * 정상 이벤트 슬롯을 점유하지 않는다(#268). markPublished 실패는 카운터를 증가시키지 않는다(멱등 재전달).
 *
 * <p>실행 주기/초기 지연은 groove.outbox.relay.{interval,initial-delay}, 주기당 처리 상한은 .batch-size,
 * 재시도 상한은 .max-attempts 다.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final Limit batchLimit;
    private final int maxAttempts;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxRelayScheduler(OutboxEventRepository repository,
                                @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                Clock clock,
                                List<OutboxEventHandler> handlers,
                                @Value("${groove.outbox.relay.batch-size:200}") int batchSize,
                                @Value("${groove.outbox.relay.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
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
            // 핸들러 미등록 — 발행을 보류한다(재시도 카운터 증가 없음).
            log.warn("아웃박스 릴레이: 핸들러 없음 eventType={}, id={} — 발행 보류", event.getEventType(), event.getId());
            return;
        }
        try {
            handler.handle(event);
        } catch (RuntimeException e) {
            recordFailure(event, e);
            return;
        }
        // 컨슈머 커밋 후 markPublished 가 실패해도 카운터는 증가시키지 않는다 — 행이 미발행으로 남아 멱등 재전달된다.
        try {
            requiresNewTx.executeWithoutResult(status -> repository.markPublished(event.getId(), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("아웃박스 발행 표시 실패: eventType={}, id={} — 다음 주기에 재시도(멱등)", event.getEventType(), event.getId(), e);
        }
    }

    /** 핸들러 실패 — 재시도 카운터를 독립 트랜잭션으로 증가시키고, 임계값 도달 시 DLQ 격리를 로그로 알린다. */
    private void recordFailure(OutboxEvent event, RuntimeException cause) {
        requiresNewTx.executeWithoutResult(status -> repository.incrementAttemptCount(event.getId()));
        int attempts = event.getAttemptCount() + 1;
        if (attempts >= maxAttempts) {
            log.error("아웃박스 릴레이 DLQ 격리: eventType={}, id={}, attempts={}/{} — 재시도 중단(수동 조치 필요)",
                    event.getEventType(), event.getId(), attempts, maxAttempts, cause);
        } else {
            log.warn("아웃박스 릴레이 실패: eventType={}, id={}, attempts={}/{} — 다음 주기에 재시도",
                    event.getEventType(), event.getId(), attempts, maxAttempts, cause);
        }
    }
}
