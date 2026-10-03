package com.groove.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import com.groove.global.common.PageLimits;
import com.groove.product.entity.BarcodeNormalizer;
import com.groove.product.entity.CatalogNoNormalizer;
import com.groove.product.entity.EditionType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductSearchRequest(
		@Size(max = 100, message = "검색어는 100자 이하여야 합니다.") String keyword,
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
		@PositiveOrZero @Max(PageLimits.MAX_PAGE) Integer page,
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

	/**
	 * 공백·하이픈을 뺀 값이 숫자 8~14자리면 바코드 정확일치 대상이다(LIKE 는 적용하지 않는다).
	 * 저장값과 같은 규칙으로 정규화해야 하이픈을 섞어 등록한 바코드도 숫자만으로 찾힌다.
	 */
	private static String extractBarcode(String keyword) {
		String normalized = BarcodeNormalizer.normalize(keyword);
		if (normalized == null || !BARCODE_PATTERN.matcher(normalized).matches()) {
			return null;
		}
		return normalized;
	}

	/**
	 * 영숫자·하이픈 조합은 정규화한 값으로 카탈로그 번호 정확일치를 함께 검색한다. 원문이 순수 숫자 8~14자리면 바코드로만 본다.
	 * 하이픈 섞인 숫자(예: 7559-61071-1)는 바코드 후보이면서 카탈로그 번호로도 남겨 바코드 분기에서 OR 로 본다.
	 */
	private static String extractCatalogNoNormalized(String keyword) {
		if (keyword == null || BARCODE_PATTERN.matcher(keyword).matches()
				|| !CATALOG_NO_PATTERN.matcher(keyword).matches()) {
			return null;
		}
		return CatalogNoNormalizer.normalize(keyword);
	}
}
