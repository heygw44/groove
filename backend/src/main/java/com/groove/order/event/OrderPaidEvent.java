package com.groove.order.event;

/**
 * 주문 결제 완료 이벤트 — 결제 PAID 확정 후 후속 처리(배송 생성 등)의 트리거.
 *
 * <p>결제 결과 콜백 트랜잭션({@code PaymentCallbackService.applyResult}) 안에서 아웃박스에 기록된다(#237) —
 * PAID 상태 변경과 같은 트랜잭션으로 원자 커밋돼 유실되지 않는다. {@code OutboxRelayScheduler} 가 미발행
 * 이벤트를 컨슈머({@code OrderPaidOutboxHandler} → 배송 생성)에 at-least-once 로 디스패치한다. 이 record 는
 * 아웃박스 payload 로 JSON 직렬화되므로 JSON 왕복 가능한 단순 record 를 유지한다.
 *
 * @param orderId     주문 식별자
 * @param orderNumber 주문 외부 식별자
 * @param memberId    회원 식별자 (게스트 주문이면 {@code null})
 * @param paymentId   결제 식별자
 */
public record OrderPaidEvent(Long orderId, String orderNumber, Long memberId, Long paymentId) {

    /** 아웃박스 {@code aggregate_type} — 이벤트를 일으킨 Aggregate 종류. */
    public static final String OUTBOX_AGGREGATE_TYPE = "ORDER";

    /** 아웃박스 {@code event_type} — 릴레이가 컨슈머를 찾는 키. */
    public static final String OUTBOX_EVENT_TYPE = "ORDER_PAID";
}
