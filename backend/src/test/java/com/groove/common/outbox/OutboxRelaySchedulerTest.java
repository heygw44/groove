package com.groove.common.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 아웃박스 릴레이 스케줄러 단위 테스트 — 디스패치 + 발행 완료 표시 + 실패 시 미표시(재시도) + 미등록 핸들러 보류.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayScheduler")
class OutboxRelaySchedulerTest {

    private static final String EVENT_TYPE = "ORDER_PAID";
    private static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");
    private static final int MAX_ATTEMPTS = 5;

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private OutboxEventHandler handler;
    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(handler.eventType()).thenReturn(EVENT_TYPE);
        // 실제 TransactionTemplate + mock TM 으로 executeWithoutResult 콜백(markPublished)이 실행되게 한다 (도달 안 하는 케이스가 있어 lenient)
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new OutboxRelayScheduler(repository, tx, clock, List.of(handler), 200, MAX_ATTEMPTS);
    }

    private OutboxEvent event(long id, String eventType) {
        return event(id, eventType, 0);
    }

    private OutboxEvent event(long id, String eventType, int attemptCount) {
        OutboxEvent event = OutboxEvent.of("ORDER", 7L, eventType, "{\"orderId\":7}");
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "attemptCount", attemptCount);
        return event;
    }

    @Test
    @DisplayName("미발행 이벤트를 핸들러에 디스패치하고 발행 완료로 표시한다")
    void dispatchesAndMarksPublished() {
        OutboxEvent event = event(1L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class))).willReturn(List.of(event));

        scheduler.relayPendingEvents();

        verify(handler).handle(event);
        verify(repository).markPublished(1L, NOW);
    }

    @Test
    @DisplayName("핸들러가 실패하면 발행 완료로 표시하지 않고 재시도 카운터를 증가시킨다 — 다음 주기에 재시도 (at-least-once)")
    void handlerFailure_incrementsAttemptCount_andDoesNotMarkPublished() {
        OutboxEvent event = event(1L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class))).willReturn(List.of(event));
        org.mockito.BDDMockito.willThrow(new RuntimeException("consumer down")).given(handler).handle(event);

        scheduler.relayPendingEvents();

        verify(repository).incrementAttemptCount(1L);
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("같은 배치에서 한 건이 실패해도 다른 건은 정상 발행된다 — 건별 격리 + 실패 건만 카운터 증가")
    void mixedBatch_failureIsolatedFromSuccess() {
        OutboxEvent poison = event(1L, EVENT_TYPE);
        OutboxEvent healthy = event(2L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class)))
                .willReturn(List.of(poison, healthy));
        org.mockito.BDDMockito.willThrow(new RuntimeException("consumer down")).given(handler).handle(poison);

        scheduler.relayPendingEvents();

        // 실패 건: 카운터 증가, 발행 미표시
        verify(repository).incrementAttemptCount(1L);
        verify(repository, never()).markPublished(eq(1L), any());
        // 정상 건: 발행 완료 표시, 카운터 미증가
        verify(repository).markPublished(2L, NOW);
        verify(repository, never()).incrementAttemptCount(2L);
    }

    @Test
    @DisplayName("재시도 상한 직전 이벤트가 다시 실패하면 카운터를 증가시켜 DLQ 임계에 도달시킨다")
    void reachingMaxAttempts_stillIncrements() {
        OutboxEvent event = event(1L, EVENT_TYPE, MAX_ATTEMPTS - 1);
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class))).willReturn(List.of(event));
        org.mockito.BDDMockito.willThrow(new RuntimeException("consumer down")).given(handler).handle(event);

        scheduler.relayPendingEvents();

        verify(repository).incrementAttemptCount(1L);
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("핸들러가 없는 이벤트도 재시도 카운터를 증가시켜 결국 DLQ 격리한다 — 디스패치/발행표시는 없음")
    void noHandler_incrementsAttemptCount() {
        OutboxEvent event = event(1L, "UNKNOWN_TYPE");
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class))).willReturn(List.of(event));

        scheduler.relayPendingEvents();

        verify(handler, never()).handle(any());
        verify(repository, never()).markPublished(any(), any());
        verify(repository).incrementAttemptCount(1L);
    }

    @Test
    @DisplayName("재시도 카운터 증가가 실패해도 같은 배치의 다른 이벤트는 정상 발행된다 — 건별 격리")
    void incrementFailure_doesNotAbortBatch() {
        OutboxEvent poison = event(1L, EVENT_TYPE);
        OutboxEvent healthy = event(2L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class)))
                .willReturn(List.of(poison, healthy));
        org.mockito.BDDMockito.willThrow(new RuntimeException("consumer down")).given(handler).handle(poison);
        // 카운터 증가 트랜잭션이 실패하는 상황 재현
        org.mockito.BDDMockito.willThrow(new RuntimeException("db down")).given(repository).incrementAttemptCount(1L);

        scheduler.relayPendingEvents(); // 예외가 배치 밖으로 전파되지 않아야 한다

        verify(repository).markPublished(2L, NOW); // 정상 건은 계속 처리됨
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 아무 것도 하지 않는다")
    void empty_noop() {
        given(repository.findByPublishedAtIsNullAndAttemptCountLessThanOrderByIdAsc(anyInt(), any(Limit.class))).willReturn(List.of());

        scheduler.relayPendingEvents();

        verify(handler, never()).handle(any());
        verify(repository, never()).markPublished(any(), any());
    }
}
