package com.groove.order.dto;

import java.time.LocalDateTime;

import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderClaimType;

public record AdminOrderClaimSummaryResponse(
		Long claimId,
		OrderClaimType type,
		OrderClaimStatus status,
		String productOrderNumber,
		String orderNumber,
		String memberEmail,
		String productName,
		String reason,
		LocalDateTime requestedAt,
		boolean refundInProgress
) {

	public static AdminOrderClaimSummaryResponse from(OrderClaim claim, boolean refundInProgress) {
		return new AdminOrderClaimSummaryResponse(claim.getId(), claim.getType(), claim.getStatus(),
				claim.getOrderItem().getProductOrderNumber(), claim.getOrderItem().getOrder().getOrderNumber(),
				claim.getOrderItem().getOrder().getMember().getEmail(), claim.getOrderItem().getProductName(),
				claim.getReason(), claim.getRequestedAt(), refundInProgress);
	}
}
