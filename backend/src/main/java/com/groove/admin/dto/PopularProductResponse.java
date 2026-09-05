package com.groove.admin.dto;

import java.math.BigDecimal;

/** 인기 상품 통계 한 행. */
public record PopularProductResponse(
		Long productId,
		String productTitle,
		String artistName,
		long soldQuantity,
		BigDecimal salesAmount,
		long orderCount
) {
}
