package com.groove.common.outbox;

/**
 * 아웃박스 이벤트 컨슈머 (#237) — {@code OutboxRelayScheduler} 가 {@link #eventType()} 로 핸들러를 찾아
 * 미발행 이벤트를 디스패치한다.
 *
 * <p><b>멱등 계약</b>: 릴레이는 at-least-once 다(디스패치 성공 후 발행 완료 표시 사이에 장애가 나면 재전달).
 * 따라서 구현은 같은 이벤트를 여러 번 받아도 부수효과가 정확히 1회여야 한다(예: {@code existsByOrderId} 선검사
 * + DB UNIQUE 방어선). 일시 실패는 {@link RuntimeException} 으로 전파하면 다음 주기에 재시도된다 — "이미 처리됨"
 * 류의 무해한 충돌은 구현이 흡수해 정상 종료(발행 완료 표시)하는 것이 바람직하다.
 *
 * <p>새 후속 처리(예: 알림)는 같은 이벤트에 대해 이 인터페이스를 구현한 빈을 추가하기만 하면 릴레이가 자동으로
 * 디스패치한다.
 */
public interface OutboxEventHandler {

    /** 이 핸들러가 처리하는 이벤트 종류 ({@link OutboxEvent#getEventType()} 와 매칭). 핸들러당 고유해야 한다. */
    String eventType();

    /** 이벤트를 처리한다. 멱등해야 하며, 재시도가 필요한 일시 실패는 예외로 전파한다. */
    void handle(OutboxEvent event);
}
