package com.groove.admin.dto;

import java.time.LocalDate;

/** {@link com.groove.admin.mapper.AdminStatsMapper#findPopularProducts} 조회 조건. */
public record PopularProductStatsCondition(
		LocalDate from,
		LocalDate to,
		int limit,
		PopularProductSortType sort
) {
}
