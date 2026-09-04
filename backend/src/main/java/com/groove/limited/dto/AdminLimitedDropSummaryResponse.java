package com.groove.limited.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDropStatus;

public record AdminLimitedDropSummaryResponse(
		Long id,
		Long productId,
		String productTitle,
		int totalQuantity,
		int soldCount,
		int perMemberLimit,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LimitedDropStatus status,
		LocalDateTime createdAt
) {
}
