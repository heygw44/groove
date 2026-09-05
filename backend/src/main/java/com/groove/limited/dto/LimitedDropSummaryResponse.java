package com.groove.limited.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.groove.limited.entity.LimitedDropStatus;
import com.groove.recommend.dto.TasteMatchResponse;

public record LimitedDropSummaryResponse(
		Long id,
		ProductSummary product,
		int totalQuantity,
		int remainingQuantity,
		int perMemberLimit,
		OffsetDateTime openAt,
		OffsetDateTime closeAt,
		LimitedDropStatus status,
		TasteMatchResponse tasteMatch
) {

	public static LimitedDropSummaryResponse from(LimitedDropSummaryRow row, int remainingQuantity,
			TasteMatchResponse tasteMatch, ZoneId zone) {
		ProductSummary product = new ProductSummary(row.productId(), row.productTitle(), row.artistName(),
				row.price(), row.thumbnailUrl());
		return new LimitedDropSummaryResponse(
				row.id(),
				product,
				row.totalQuantity(),
				remainingQuantity,
				row.perMemberLimit(),
				row.openAt().atZone(zone).toOffsetDateTime(),
				row.closeAt().atZone(zone).toOffsetDateTime(),
				row.status(),
				tasteMatch);
	}

	public record ProductSummary(Long id, String title, String artistName, BigDecimal price, String thumbnailUrl) {
	}
}
