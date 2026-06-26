package com.groove.common.outbox;

import com.groove.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * 이벤트 아웃박스 레코드. 상태 변경과 같은 트랜잭션에서 후속 이벤트를 이 행으로 기록한다.
 * OutboxRelayScheduler 가 publishedAt IS NULL(미발행) 행을 주기 발행하고 성공 시 markPublished 한다.
 * 생성은 of 팩토리만 허용하며 항상 미발행으로 시작한다. payload 는 이벤트 본문 JSON 직렬화.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                // 릴레이 조회 published_at IS NULL AND attempt_count < N 를 위해 attempt_count 포함 — DLQ 격리 행을
                // 인덱스 레벨에서 제외한다(V30, #268). 정리 쿼리는 published_at 선두를 활용.
                @Index(name = "idx_outbox_unpublished", columnList = "published_at, attempt_count, id")
        }
)
public class OutboxEvent extends BaseTimeEntity {

    /** DB aggregate_type 컬럼 길이. */
    static final int MAX_AGGREGATE_TYPE_LENGTH = 50;
    /** DB event_type 컬럼 길이. */
    static final int MAX_EVENT_TYPE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트를 일으킨 Aggregate 종류 (예: ORDER). */
    @Column(name = "aggregate_type", nullable = false, length = MAX_AGGREGATE_TYPE_LENGTH)
    private String aggregateType;

    /** 이벤트를 일으킨 Aggregate 식별자 (예: orderId). */
    @Column(name = "aggregate_id", nullable = false)
    private long aggregateId;

    /** 이벤트 종류 — 릴레이가 핸들러를 찾는 키 (예: ORDER_PAID). */
    @Column(name = "event_type", nullable = false, length = MAX_EVENT_TYPE_LENGTH)
    private String eventType;

    /** 이벤트 본문 JSON 직렬화. */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** 발행 완료 시각 — null 이면 미발행(릴레이 대상). */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * 핸들러 실패 누적 횟수 — 릴레이 조회는 attempt_count < max-attempts 인 미발행 행만 대상으로 한다.
     * 임계값에 도달한 이벤트는 DLQ(격리)로 더 이상 디스패치되지 않는다(#268).
     */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    protected OutboxEvent() {
    }

    private OutboxEvent(String aggregateType, long aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    /** 아웃박스 행 생성. 항상 미발행(publishedAt == null)으로 시작한다. */
    public static OutboxEvent of(String aggregateType, long aggregateId, String eventType, String payload) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (aggregateType.length() > MAX_AGGREGATE_TYPE_LENGTH) {
            throw new IllegalArgumentException("aggregateType length must be <= " + MAX_AGGREGATE_TYPE_LENGTH);
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventType.length() > MAX_EVENT_TYPE_LENGTH) {
            throw new IllegalArgumentException("eventType length must be <= " + MAX_EVENT_TYPE_LENGTH);
        }
        Objects.requireNonNull(payload, "payload must not be null");
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    /** 발행 완료로 표시한다. */
    public void markPublished(Instant now) {
        this.publishedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }
}
