package com.groove.admin.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDropStatus;

/** 한정반 드롭 현황 통계 한 행. soldOutAt/soldOutSeconds 는 SOLD_OUT 상태가 아니면 null 이다. */
public record LimitedDropStatsResponse(
		Long dropId,
		String productTitle,
		LimitedDropStatus status,
		int totalQuantity,
		int soldQuantity,
		double sellRate,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LocalDateTime soldOutAt,
		Long soldOutSeconds
) {
}
