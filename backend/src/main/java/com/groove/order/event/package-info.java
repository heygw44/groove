/**
 * 주문 도메인 이벤트.
 *
 * OrderPaidEvent — 결제 완료 시 트랜잭셔널 아웃박스에 기록되고,
 * OutboxRelayScheduler 가 미발행 이벤트를 멱등 컨슈머에 at-least-once 로 발행한다.
 */
package com.groove.order.event;
