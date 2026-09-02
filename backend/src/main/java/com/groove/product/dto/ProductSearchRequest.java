package com.groove.product.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductSearchRequest(
		String keyword,
		Long artistId,
		Long genreId,
		Long labelId,
		@DecimalMin("0") BigDecimal minPrice,
		@DecimalMin("0") BigDecimal maxPrice,
		String sort,
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public ProductSearchCondition toCondition() {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return new ProductSearchCondition(keyword, artistId, genreId, labelId, minPrice, maxPrice,
				ProductSortType.from(sort), resolvedPage, resolvedSize);
	}
}
