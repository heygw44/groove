package com.groove.common.outbox;

/**
 * 아웃박스 이벤트 컨슈머 — OutboxRelayScheduler 가 eventType 으로 핸들러를 찾아 미발행 이벤트를 디스패치한다.
 *
 * <p>릴레이는 at-least-once 이므로 구현은 멱등해야 한다(같은 이벤트를 여러 번 받아도 부수효과 1회). 일시 실패는
 * RuntimeException 으로 전파하면 다음 주기에 재시도된다.
 */
public interface OutboxEventHandler {

    /** 이 핸들러가 처리하는 이벤트 종류 (OutboxEvent#getEventType 과 매칭). 핸들러당 고유해야 한다. */
    String eventType();

    /** 이벤트를 처리한다. 멱등해야 하며, 일시 실패는 예외로 전파한다. */
    void handle(OutboxEvent event);
}
