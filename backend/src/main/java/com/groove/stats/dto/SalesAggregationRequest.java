package com.groove.stats.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** 관리자 수동 재집계 요청. 운영 백필의 유일한 경로다. */
public record SalesAggregationRequest(
		@NotNull LocalDate from,
		@NotNull LocalDate to
) {
}
