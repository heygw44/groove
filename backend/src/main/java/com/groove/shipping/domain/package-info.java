/**
 * 배송 도메인 모델 — Shipping 엔티티 + 상태 enum
 * (ShippingStatus) + ShippingRepository.
 * 주문당 배송 1건(order_id UNIQUE), 배송지는 주문 시점 스냅샷(OrderShippingInfo) 복사,
 * 상태 전이(PREPARING→SHIPPED→DELIVERED)는 자동 진행 스케줄러가 한 단계씩 밀어준다.
 */
package com.groove.shipping.domain;
