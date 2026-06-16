package com.groove.order.event;

/**
 * 주문 결제 완료 이벤트 — 결제 PAID 확정 후 후속 처리(배송 생성 등)의 트리거.
 * 결제 콜백 트랜잭션 안에서 아웃박스에 기록되고, OutboxRelayScheduler 가 컨슈머에 at-least-once 로 디스패치한다.
 * memberId 는 게스트 주문이면 null.
 */
public record OrderPaidEvent(Long orderId, String orderNumber, Long memberId, Long paymentId) {

    /** 아웃박스 aggregate_type. */
    public static final String OUTBOX_AGGREGATE_TYPE = "ORDER";

    /** 아웃박스 event_type. */
    public static final String OUTBOX_EVENT_TYPE = "ORDER_PAID";
}
