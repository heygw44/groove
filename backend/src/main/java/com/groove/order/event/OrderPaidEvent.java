package com.groove.order.event;

/**
 * 결제 PAID 확정 후 후속 처리 트리거. 결제 콜백 트랜잭션 안에서 아웃박스에 기록되고
 * OutboxRelayScheduler 가 at-least-once 로 디스패치한다. memberId 는 게스트면 null.
 */
public record OrderPaidEvent(Long orderId, String orderNumber, Long memberId, Long paymentId) {

    public static final String OUTBOX_AGGREGATE_TYPE = "ORDER";

    public static final String OUTBOX_EVENT_TYPE = "ORDER_PAID";
}
