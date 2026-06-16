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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 아웃박스 릴레이 스케줄러 단위 테스트 (#237) — 디스패치 + 발행 완료 표시 + 실패 시 미표시(재시도) + 미등록 핸들러 보류.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayScheduler")
class OutboxRelaySchedulerTest {

    private static final String EVENT_TYPE = "ORDER_PAID";
    private static final Instant NOW = Instant.parse("2026-06-16T00:00:00Z");

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
        // 실제 TransactionTemplate + mock TM 으로 executeWithoutResult 콜백(markPublished)이 실제로 실행되게 한다.
        // markPublished 에 도달하지 않는 케이스(핸들러 실패/미등록/빈 배치)도 있어 lenient 로 둔다.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        scheduler = new OutboxRelayScheduler(repository, tx, clock, List.of(handler), 200);
    }

    private OutboxEvent event(long id, String eventType) {
        OutboxEvent event = OutboxEvent.of("ORDER", 7L, eventType, "{\"orderId\":7}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    @DisplayName("미발행 이벤트를 핸들러에 디스패치하고 발행 완료로 표시한다")
    void dispatchesAndMarksPublished() {
        OutboxEvent event = event(1L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).willReturn(List.of(event));

        scheduler.relayPendingEvents();

        verify(handler).handle(event);
        verify(repository).markPublished(1L, NOW);
    }

    @Test
    @DisplayName("핸들러가 실패하면 발행 완료로 표시하지 않는다 — 다음 주기에 재시도 (at-least-once)")
    void handlerFailure_doesNotMarkPublished() {
        OutboxEvent event = event(1L, EVENT_TYPE);
        given(repository.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).willReturn(List.of(event));
        org.mockito.BDDMockito.willThrow(new RuntimeException("consumer down")).given(handler).handle(event);

        scheduler.relayPendingEvents();

        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("핸들러가 없는 이벤트는 발행을 보류한다 — 디스패치/표시 모두 없음")
    void noHandler_holdsEvent() {
        OutboxEvent event = event(1L, "UNKNOWN_TYPE");
        given(repository.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).willReturn(List.of(event));

        scheduler.relayPendingEvents();

        verify(handler, never()).handle(any());
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    @DisplayName("미발행 이벤트가 없으면 아무 것도 하지 않는다")
    void empty_noop() {
        given(repository.findByPublishedAtIsNullOrderByIdAsc(any(Limit.class))).willReturn(List.of());

        scheduler.relayPendingEvents();

        verify(handler, never()).handle(any());
        verify(repository, never()).markPublished(any(), any());
    }
}
