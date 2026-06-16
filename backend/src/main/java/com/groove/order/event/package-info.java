/**
 * 주문 도메인 이벤트.
 *
 * <p>{@link com.groove.order.event.OrderPaidEvent} — 결제 완료 시 트랜잭셔널 아웃박스에 기록(#237,
 * {@code PaymentCallbackService}). {@code OutboxRelayScheduler} 가 미발행 이벤트를 멱등 컨슈머
 * ({@code OrderPaidOutboxHandler} → 배송 생성)에 at-least-once 로 발행한다 — 인프로세스 AFTER_COMMIT 리스너와
 * 달리 PAID 와 원자 커밋돼 프로세스 다운에도 유실되지 않는다.
 */
package com.groove.order.event;
