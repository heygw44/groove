package com.groove.limited.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.entity.LimitedPurchase;

public record AdminLimitedDropDetailResponse(
		Long id,
		Long productId,
		String productTitle,
		int totalQuantity,
		int soldCount,
		int dbRemaining,
		Integer redisRemaining,
		int perMemberLimit,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LimitedDropStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		List<AdminLimitedPurchaseResponse> purchases
) {

	public static AdminLimitedDropDetailResponse from(LimitedDrop drop, Integer redisRemaining,
			List<LimitedPurchase> purchases) {
		List<AdminLimitedPurchaseResponse> purchaseResponses = purchases.stream()
				.map(AdminLimitedPurchaseResponse::from)
				.toList();

		return new AdminLimitedDropDetailResponse(
				drop.getId(),
				drop.getProduct().getId(),
				drop.getProduct().getTitle(),
				drop.getTotalQuantity(),
				drop.getSoldCount(),
				drop.remainingQuantity(),
				redisRemaining,
				drop.getPerMemberLimit(),
				drop.getOpenAt(),
				drop.getCloseAt(),
				drop.getStatus(),
				drop.getCreatedAt(),
				drop.getUpdatedAt(),
				purchaseResponses);
	}
}
