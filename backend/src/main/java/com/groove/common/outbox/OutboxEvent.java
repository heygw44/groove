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
 * 이벤트 아웃박스 레코드 (#237 트랜잭셔널 아웃박스).
 *
 * <p>상태 변경(예: 결제 PAID)과 같은 트랜잭션에서 후속 이벤트를 이 행으로 기록(원자 커밋)한다. 릴레이
 * 스케줄러({@code OutboxRelayScheduler})가 {@code publishedAt IS NULL}(미발행) 행을 주기적으로 발행
 * (at-least-once)하고 성공 시 {@link #markPublished(Instant)} 로 발행 완료로 표시한다. 컨슈머는 멱등이라
 * 중복 발행·프로세스 재기동에도 정확히 1회 효과다.
 *
 * <p>생성은 {@link #of} 정적 팩토리만 허용한다 — 항상 미발행({@code publishedAt == null})으로 시작한다.
 * {@code payload} 는 이벤트 본문 JSON 직렬화({@code OutboxEventPublisher} 가 채움)이며, 컨슈머가 같은 타입으로
 * 역직렬화한다.
 */
@Entity
@Table(
        name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_unpublished", columnList = "published_at, id")
        }
)
public class OutboxEvent extends BaseTimeEntity {

    /** DB {@code aggregate_type} 컬럼 길이. */
    static final int MAX_AGGREGATE_TYPE_LENGTH = 50;
    /** DB {@code event_type} 컬럼 길이. */
    static final int MAX_EVENT_TYPE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트를 일으킨 Aggregate 종류 (예: {@code ORDER}) — 관측/필터용 메타. */
    @Column(name = "aggregate_type", nullable = false, length = MAX_AGGREGATE_TYPE_LENGTH)
    private String aggregateType;

    /** 이벤트를 일으킨 Aggregate 식별자 (예: orderId). */
    @Column(name = "aggregate_id", nullable = false)
    private long aggregateId;

    /** 이벤트 종류 — 릴레이가 핸들러를 찾는 키 (예: {@code ORDER_PAID}). */
    @Column(name = "event_type", nullable = false, length = MAX_EVENT_TYPE_LENGTH)
    private String eventType;

    /** 이벤트 본문 JSON 직렬화. 컨슈머가 같은 타입으로 역직렬화한다. */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** 발행 완료 시각 — {@code null} 이면 미발행(릴레이 대상). */
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String aggregateType, long aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }

    /**
     * 아웃박스 행 생성. 항상 미발행({@code publishedAt == null})으로 시작한다.
     *
     * @param aggregateType Aggregate 종류 — blank 불가, {@value #MAX_AGGREGATE_TYPE_LENGTH}자 이하
     * @param aggregateId   Aggregate 식별자
     * @param eventType     이벤트 종류 — blank 불가, {@value #MAX_EVENT_TYPE_LENGTH}자 이하
     * @param payload       이벤트 본문 JSON — null 불가
     */
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

    /** 발행 완료로 표시 — 릴레이가 컨슈머 디스패치 성공 후 호출한다. */
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
}
