package com.groove.limited.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedPurchase;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;

public record AdminLimitedPurchaseResponse(
		Long id,
		Long memberId,
		String memberNickname,
		Long orderId,
		String orderNumber,
		OrderStatus orderStatus,
		int quantity,
		LocalDateTime purchasedAt
) {

	public static AdminLimitedPurchaseResponse from(LimitedPurchase purchase) {
		Order order = purchase.getOrder();
		Long orderId = order == null ? null : order.getId();
		String orderNumber = order == null ? null : order.getOrderNumber();
		OrderStatus orderStatus = order == null ? null : order.getStatus();

		return new AdminLimitedPurchaseResponse(
				purchase.getId(),
				purchase.getMember().getId(),
				purchase.getMember().getNickname(),
				orderId,
				orderNumber,
				orderStatus,
				purchase.getQuantity(),
				purchase.getCreatedAt());
	}
}
