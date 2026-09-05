package com.groove.admin.dto;

import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 인기 상품 통계 정렬 기준. */
@Getter
@RequiredArgsConstructor
public enum PopularProductSortType {

	QUANTITY("quantity"),
	SALES("sales");

	private final String value;

	public static PopularProductSortType from(String value) {
		if (!StringUtils.hasText(value)) {
			return QUANTITY;
		}
		for (PopularProductSortType sortType : values()) {
			if (sortType.value.equals(value)) {
				return sortType;
			}
		}
		throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
	}
}
