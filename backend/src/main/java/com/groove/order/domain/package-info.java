/**
 * 주문 도메인 — 주문 모델(Order, OrderItem)과 상태 머신(OrderStatus).
 *
 * Order ↔ OrderItem 은 aggregate (cascade=ALL + orphanRemoval=true).
 * OrderItem 은 album 의 가격/제목을 스냅샷으로 보관한다.
 */
package com.groove.order.domain;
