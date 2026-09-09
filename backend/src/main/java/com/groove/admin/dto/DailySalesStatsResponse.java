package com.groove.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 일별 매출 통계 응답. {@code aggregatedAt} 은 사전 집계 기준시각이라 오차가 있을 수 있다. */
public record DailySalesStatsResponse(
		List<DailySalesResponse> items,
		LocalDateTime aggregatedAt
) {

	public static DailySalesStatsResponse of(List<DailySalesResponse> items, LocalDateTime aggregatedAt) {
		return new DailySalesStatsResponse(items, aggregatedAt);
	}
}
