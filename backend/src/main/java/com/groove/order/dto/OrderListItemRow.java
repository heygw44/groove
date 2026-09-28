package com.groove.order.dto;

import java.math.BigDecimal;

/** {@code findItemsByOrderIds} 매퍼 프로젝션 전용. 페이지 내 주문 id 로 묶어 조회한 상품 행이다. */
public record OrderListItemRow(
		Long orderId,
		Long productId,
		String productName,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl
) {

	public OrderListItemResponse toResponse() {
		return new OrderListItemResponse(productId, productName, quantity, lineAmount, thumbnailUrl);
	}
}
