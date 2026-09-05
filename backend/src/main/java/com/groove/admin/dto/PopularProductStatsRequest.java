package com.groove.admin.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** 인기 상품 통계 요청. limit/sort 검증·기본값은 서비스에서 처리한다. */
public record PopularProductStatsRequest(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		Integer limit,
		String sort
) {
}
