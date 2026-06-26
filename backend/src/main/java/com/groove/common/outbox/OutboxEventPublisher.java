package com.groove.common.outbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * 아웃박스 발행 진입점 — 이벤트를 outbox 테이블에 기록한다. 반드시 상태 변경과 같은 트랜잭션 안에서 호출해야
 * 한다. 실제 발행(컨슈머 디스패치)은 OutboxRelayScheduler 가 비동기로 수행한다. payload 는 JSON 왕복 가능한
 * 단순 record 여야 한다.
 */
@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** 이벤트를 아웃박스에 기록한다 (호출자 트랜잭션에 참여). payload 는 JSON 으로 직렬화돼 저장된다. */
    public void publish(String aggregateType, long aggregateId, String eventType, Object payload) {
        // 활성 트랜잭션 밖에서 호출되면 차단한다.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("아웃박스 발행은 활성 트랜잭션 안에서 호출해야 합니다 (상태 변경과 원자 커밋)");
        }
        String json = objectMapper.writeValueAsString(payload);
        repository.save(OutboxEvent.of(aggregateType, aggregateId, eventType, json));
    }
}
