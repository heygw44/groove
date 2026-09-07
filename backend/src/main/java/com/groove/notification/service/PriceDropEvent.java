package com.groove.notification.service;

/**
 * 상품 가격이 내려간 이벤트.
 *
 * @param productId 가격이 내려간 상품 ID
 * @param productTitle 알림 문구에 쓸 발행 시점의 상품명
 */
public record PriceDropEvent(Long productId, String productTitle) {
}
