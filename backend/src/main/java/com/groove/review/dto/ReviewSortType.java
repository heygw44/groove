package com.groove.review.dto;

import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 리뷰 목록 정렬 기준. */
@Getter
@RequiredArgsConstructor
public enum ReviewSortType {

	LATEST("latest"),
	RATING_DESC("ratingDesc"),
	RATING_ASC("ratingAsc");

	private final String value;

	public static ReviewSortType from(String value) {
		if (!StringUtils.hasText(value)) {
			return LATEST;
		}
		for (ReviewSortType sortType : values()) {
			if (sortType.value.equals(value)) {
				return sortType;
			}
		}
		throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
	}

	public Sort toSort() {
		return switch (this) {
			case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
			case RATING_DESC -> Sort.by(Sort.Direction.DESC, "rating")
					.and(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
			case RATING_ASC -> Sort.by(Sort.Direction.ASC, "rating")
					.and(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
		};
	}
}
