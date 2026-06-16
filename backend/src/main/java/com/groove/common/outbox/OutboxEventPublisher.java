package com.groove.common.outbox;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 아웃박스 발행 진입점 (#237) — 상태 변경 트랜잭션 안에서 이벤트를 outbox 테이블에 기록한다.
 *
 * <p>호출 규약: 반드시 상태 변경과 <b>같은 트랜잭션</b> 안에서 호출해야 한다(예:
 * {@code PaymentCallbackService.applyResult} 의 {@code @Transactional}). 그래야 상태 변경과 아웃박스 행이
 * 원자적으로 커밋돼, 상태는 바뀌었는데 이벤트는 유실되는(또는 그 반대) 창이 없다. 실제 발행(컨슈머 디스패치)은
 * {@code OutboxRelayScheduler} 가 비동기로 수행한다.
 *
 * <p>payload 는 JSON 왕복 가능한 단순 record 여야 한다 — 컨슈머({@code OutboxEventHandler})가 같은 타입으로
 * 역직렬화한다. 직렬화는 {@code IdempotencyService} 와 동일한 Jackson {@link ObjectMapper} 를 쓴다.
 */
@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 이벤트를 아웃박스에 기록한다 (호출자 트랜잭션에 참여).
     *
     * @param aggregateType 이벤트를 일으킨 Aggregate 종류 (예: {@code ORDER})
     * @param aggregateId   Aggregate 식별자 (예: orderId)
     * @param eventType     이벤트 종류 — 릴레이가 핸들러를 찾는 키 (예: {@code ORDER_PAID})
     * @param payload       이벤트 본문 — JSON 으로 직렬화돼 저장된다
     */
    public void publish(String aggregateType, long aggregateId, String eventType, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        repository.save(OutboxEvent.of(aggregateType, aggregateId, eventType, json));
    }
}
