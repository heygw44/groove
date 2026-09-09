package com.groove.product.dto;

import java.math.BigDecimal;

import com.groove.product.entity.EditionType;
import com.groove.product.entity.ProductStatus;

public record ProductSummaryResponse(
		Long id,
		String title,
		String artistName,
		String labelName,
		BigDecimal price,
		String colorVariant,
		String pressingInfo,
		ProductStatus status,
		String thumbnailUrl,
		Double averageRating,
		long reviewCount,
		Boolean wishlisted,
		String country,
		Integer pressingYear,
		EditionType editionType,
		Long albumId,
		int otherPressingCount
) {
}
