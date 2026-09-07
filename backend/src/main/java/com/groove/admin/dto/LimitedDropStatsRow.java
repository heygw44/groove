package com.groove.admin.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDropStatus;

/**
 * {@code findLimitedDropStats} 매퍼 프로젝션 전용. limited_drop_stat LEFT JOIN 컬럼(실패 카운트 4개)은
 * 해당 드롭에 아직 집계 행이 없으면 전부 null 이다.
 */
public record LimitedDropStatsRow(
		Long dropId,
		String productTitle,
		LimitedDropStatus status,
		int totalQuantity,
		int soldQuantity,
		double sellRate,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LocalDateTime soldOutAt,
		Long soldOutSeconds,
		Long soldOutCount,
		Long alreadyPurchasedCount,
		Long notOpenCount,
		Long closedCount
) {
}
