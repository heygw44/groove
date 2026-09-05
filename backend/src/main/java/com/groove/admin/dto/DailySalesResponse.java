package com.groove.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 일별 매출 통계 한 행. */
public record DailySalesResponse(
		LocalDate date,
		long orderCount,
		BigDecimal salesAmount,
		BigDecimal cancelAmount
) {

	public static DailySalesResponse empty(LocalDate date) {
		return new DailySalesResponse(date, 0, BigDecimal.ZERO, BigDecimal.ZERO);
	}
}
