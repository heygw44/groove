package com.groove.limited.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.groove.limited.dto.LimitedDropSummaryResponse.ProductSummary;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.product.entity.Product;

public record LimitedDropDetailResponse(
		Long id,
		ProductSummary product,
		int totalQuantity,
		int remainingQuantity,
		int perMemberLimit,
		OffsetDateTime openAt,
		OffsetDateTime closeAt,
		LimitedDropStatus status,
		Boolean purchased,
		OffsetDateTime serverTime
) {

	public static LimitedDropDetailResponse from(LimitedDrop drop, String thumbnailUrl, int remainingQuantity,
			Boolean purchased, OffsetDateTime serverTime, ZoneId zone) {
		Product product = drop.getProduct();
		ProductSummary productSummary = new ProductSummary(product.getId(), product.getTitle(),
				product.getArtist().getName(), product.getPrice(), thumbnailUrl);
		return new LimitedDropDetailResponse(
				drop.getId(),
				productSummary,
				drop.getTotalQuantity(),
				remainingQuantity,
				drop.getPerMemberLimit(),
				drop.getOpenAt().atZone(zone).toOffsetDateTime(),
				drop.getCloseAt().atZone(zone).toOffsetDateTime(),
				drop.getStatus(),
				purchased,
				serverTime);
	}
}
