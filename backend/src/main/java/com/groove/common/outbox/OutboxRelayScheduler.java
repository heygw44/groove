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
 * 아웃박스 릴레이 스케줄러 (#237) — 미발행 이벤트를 주기적으로 컨슈머에 디스패치하고 발행 완료로 표시한다
 * (at-least-once). {@code PaymentReconciliationScheduler} 와 동일한 골격이다.
 *
 * <p>처리: {@code published_at IS NULL} 행을 id FIFO 로 {@code .batch-size} 만큼 조회 → 건별로
 * {@link OutboxEventHandler} 디스패치 → 성공 시 {@code markPublished}(독립 트랜잭션). 한 건의 실패가 배치
 * 전체를 막지 않도록 건별로 격리하고(다음 주기에 재시도), 스케줄러 스레드 밖으로 예외를 흘리지 않는다.
 *
 * <p><b>정확히 1회 효과</b>: 컨슈머가 커밋한 뒤 {@code markPublished} 가 실패하면 행이 미발행으로 남아 다음
 * 주기에 재디스패치되지만, 컨슈머가 멱등({@link OutboxEventHandler} 계약)이라 부수효과는 1회다 — 프로세스
 * 재기동·중복 발행에도 동일. 인프로세스 {@code @TransactionalEventListener} 와 달리 커밋된 이벤트가 유실되지 않는다.
 *
 * <p>알려진 한계(v1): 재시도 횟수 상한·지수 백오프·dead-letter 가 없다. 역직렬화 불가 payload(스키마 드리프트 등)나
 * 영구 실패 핸들러 같은 "poison" 이벤트는 매 주기 재시도되며(건별 격리라 같은 배치의 다른 이벤트는 정상 처리됨), TTL
 * 정리는 발행 완료 행만 지우므로 미발행으로 잔존한다. 배송 한정으로는 {@code ShippingReconciliationScheduler}(#169)가
 * PAID 고아 주문을 보충해 backstop 하지만, 일반 컨슈머의 dead-letter/attempt-cap 은 후속 과제다.
 *
 * <p>실행 주기/초기 지연은 {@code groove.outbox.relay.{interval,initial-delay}}, 주기당 처리 상한은
 * {@code .batch-size}. 전역 {@code @EnableScheduling} 은 {@code common.scheduling.SchedulingConfig} 에 있다.
 */
@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository repository;
    private final TransactionTemplate requiresNewTx;
    private final Clock clock;
    private final Limit batchLimit;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxRelayScheduler(OutboxEventRepository repository,
                                @Qualifier(CommonTransactionConfig.REQUIRES_NEW_TX_TEMPLATE) TransactionTemplate requiresNewTx,
                                Clock clock,
                                List<OutboxEventHandler> handlers,
                                @Value("${groove.outbox.relay.batch-size:200}") int batchSize) {
        this.repository = repository;
        this.requiresNewTx = requiresNewTx;
        this.clock = clock;
        if (batchSize <= 0) {
            throw new IllegalArgumentException("groove.outbox.relay.batch-size 는 양수여야 합니다: " + batchSize);
        }
        this.batchLimit = Limit.of(batchSize);
        // eventType 중복은 toMap 이 즉시 예외로 알린다 — 같은 종류에 핸들러 둘은 디스패치 모호성이므로 빈 생성 시점에 차단.
        this.handlers = handlers.stream().collect(Collectors.toMap(OutboxEventHandler::eventType, Function.identity()));
    }

    @Scheduled(
            fixedDelayString = "${groove.outbox.relay.interval:PT2S}",
            initialDelayString = "${groove.outbox.relay.initial-delay:PT5S}")
    public void relayPendingEvents() {
        List<OutboxEvent> pending = repository.findByPublishedAtIsNullOrderByIdAsc(batchLimit);
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
            // 핸들러 미등록 — 발행을 보류해 둔다(추후 핸들러가 추가되면 처리). 정상 흐름에선 발생하지 않는다.
            log.warn("아웃박스 릴레이: 핸들러 없음 eventType={}, id={} — 발행 보류", event.getEventType(), event.getId());
            return;
        }
        try {
            handler.handle(event);
            requiresNewTx.executeWithoutResult(status -> repository.markPublished(event.getId(), clock.instant()));
        } catch (RuntimeException e) {
            log.warn("아웃박스 릴레이 실패: eventType={}, id={} — 다음 주기에 재시도", event.getEventType(), event.getId(), e);
        }
    }
}
