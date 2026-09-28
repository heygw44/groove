package com.groove.order.dto;

import java.math.BigDecimal;

/** 주문 목록 화면에 노출하는 주문 상품 행. */
public record OrderListItemResponse(
		Long productId,
		String productName,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl
) {
}
