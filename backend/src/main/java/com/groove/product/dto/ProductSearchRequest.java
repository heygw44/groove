package com.groove.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import com.groove.product.entity.CatalogNoNormalizer;
import com.groove.product.entity.EditionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductSearchRequest(
		String keyword,
		Long artistId,
		List<Long> genreIds,
		Long labelId,
		Long albumId,
		String country,
		Integer pressingYearFrom,
		Integer pressingYearTo,
		EditionType editionType,
		@DecimalMin("0") BigDecimal minPrice,
		@DecimalMin("0") BigDecimal maxPrice,
		String sort,
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{8,14}$");
	private static final Pattern CATALOG_NO_PATTERN = Pattern.compile("^[A-Za-z0-9-]+$");

	public ProductSearchCondition toCondition(Long memberId) {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		List<Long> resolvedGenreIds = genreIds == null ? List.of() : genreIds;
		return new ProductSearchCondition(keyword, artistId, resolvedGenreIds, labelId, albumId, country,
				pressingYearFrom, pressingYearTo, editionType, extractBarcode(keyword),
				extractCatalogNoNormalized(keyword), minPrice, maxPrice, ProductSortType.from(sort), resolvedPage,
				resolvedSize, memberId);
	}

	/** 숫자 8~14자리는 바코드 정확일치 대상이다(LIKE 는 적용하지 않는다). */
	private static String extractBarcode(String keyword) {
		if (keyword == null || !BARCODE_PATTERN.matcher(keyword).matches()) {
			return null;
		}
		return keyword;
	}

	/** 영숫자·하이픈 조합은 정규화한 값으로 카탈로그 번호 정확일치를 제목/아티스트명 LIKE 와 OR 로 함께 검색한다. */
	private static String extractCatalogNoNormalized(String keyword) {
		if (keyword == null || BARCODE_PATTERN.matcher(keyword).matches()
				|| !CATALOG_NO_PATTERN.matcher(keyword).matches()) {
			return null;
		}
		return CatalogNoNormalizer.normalize(keyword);
	}
}
