package com.groove.admin.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** 조회 기간만 받는 통계 API 공통 요청. from/to 를 생략하면 서비스가 기본값을 채운다. */
public record StatsPeriodRequest(
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {
}
