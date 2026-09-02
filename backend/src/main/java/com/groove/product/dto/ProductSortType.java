package com.groove.product.dto;

import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 상품 목록 정렬 기준. */
@Getter
@RequiredArgsConstructor
public enum ProductSortType {

	LATEST("latest"),
	PRICE_ASC("priceAsc"),
	PRICE_DESC("priceDesc"),
	RATING("rating"),
	POPULAR("popular");

	private final String value;

	public static ProductSortType from(String value) {
		if (!StringUtils.hasText(value)) {
			return LATEST;
		}
		for (ProductSortType sortType : values()) {
			if (sortType.value.equals(value)) {
				return sortType;
			}
		}
		throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
	}
}
