package com.groove.limited.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDropStatus;

/** {@code findPublicSummaries} JPQL 프로젝션 전용. */
public record LimitedDropSummaryRow(
		Long id,
		Long productId,
		String productTitle,
		String artistName,
		BigDecimal price,
		String thumbnailUrl,
		int totalQuantity,
		int soldCount,
		int perMemberLimit,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LimitedDropStatus status
) {
}
