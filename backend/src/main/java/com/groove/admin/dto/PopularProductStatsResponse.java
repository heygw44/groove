package com.groove.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 인기 상품 통계 응답. {@code aggregatedAt} 은 사전 집계 기준시각이라 오차가 있을 수 있다. */
public record PopularProductStatsResponse(
		List<PopularProductResponse> items,
		LocalDateTime aggregatedAt
) {

	public static PopularProductStatsResponse of(List<PopularProductResponse> items, LocalDateTime aggregatedAt) {
		return new PopularProductStatsResponse(items, aggregatedAt);
	}
}
