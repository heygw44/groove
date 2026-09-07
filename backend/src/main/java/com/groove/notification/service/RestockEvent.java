package com.groove.notification.service;

/**
 * 품절이던 상품의 재고가 다시 채워진 이벤트.
 *
 * @param productId 재입고된 상품 ID
 * @param productTitle 알림 문구에 쓸 발행 시점의 상품명
 */
public record RestockEvent(Long productId, String productTitle) {
}
