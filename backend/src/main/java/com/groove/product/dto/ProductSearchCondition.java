package com.groove.product.dto;

import java.math.BigDecimal;
import java.util.List;

import com.groove.product.entity.EditionType;

/** {@link com.groove.product.mapper.ProductSearchMapper} 에 전달되는 검색 조건. */
public record ProductSearchCondition(
		String keyword,
		Long artistId,
		List<Long> genreIds,
		Long labelId,
		Long albumId,
		String country,
		Integer pressingYearFrom,
		Integer pressingYearTo,
		EditionType editionType,
		String barcode,
		String catalogNoNormalized,
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
