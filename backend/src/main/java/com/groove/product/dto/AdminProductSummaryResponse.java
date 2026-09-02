package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.product.entity.ProductStatus;

public record AdminProductSummaryResponse(
		Long id,
		String title,
		String artistName,
		String labelName,
		BigDecimal price,
		ProductStatus status,
		String thumbnailUrl,
		Integer stockQuantity,
		LocalDateTime createdAt
) {
}
