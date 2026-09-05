package com.groove.limited.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;

public record AdminLimitedDropResponse(
		Long id,
		Long productId,
		String productTitle,
		int totalQuantity,
		int soldCount,
		int remainingQuantity,
		int perMemberLimit,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LimitedDropStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static AdminLimitedDropResponse from(LimitedDrop drop) {
		return new AdminLimitedDropResponse(
				drop.getId(),
				drop.getProduct().getId(),
				drop.getProduct().getTitle(),
				drop.getTotalQuantity(),
				drop.getSoldCount(),
				drop.remainingQuantity(),
				drop.getPerMemberLimit(),
				drop.getOpenAt(),
				drop.getCloseAt(),
				drop.getStatus(),
				drop.getCreatedAt(),
				drop.getUpdatedAt());
	}
}
