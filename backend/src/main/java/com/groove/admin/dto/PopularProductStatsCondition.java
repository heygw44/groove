package com.groove.admin.dto;

import java.time.LocalDateTime;

/** {@link com.groove.admin.mapper.AdminStatsMapper#findPopularProducts} 조회 조건. */
public record PopularProductStatsCondition(
		LocalDateTime fromAt,
		LocalDateTime toExclusiveAt,
		int limit,
		PopularProductSortType sort
) {
}
