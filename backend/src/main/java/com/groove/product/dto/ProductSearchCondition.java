package com.groove.product.dto;

import java.math.BigDecimal;
import java.util.List;

/** {@link com.groove.product.mapper.ProductSearchMapper} 에 전달되는 검색 조건. */
public record ProductSearchCondition(
		String keyword,
		Long artistId,
		List<Long> genreIds,
		Long labelId,
		BigDecimal minPrice,
		BigDecimal maxPrice,
		ProductSortType sort,
		int page,
		int size,
		Long memberId
) {

	public int offset() {
		return page * size;
	}
}
