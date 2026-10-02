package com.groove.member.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.order.dto.OrderSummaryResponse;
import com.groove.order.entity.OrderStatus;

public record AdminMemberRecentOrderResponse(
		Long id,
		String orderNumber,
		OrderStatus status,
		BigDecimal finalAmount,
		BigDecimal canceledAmount,
		String representativeProductName,
		int itemCount,
		LocalDateTime createdAt
) {

	public static AdminMemberRecentOrderResponse of(OrderSummaryResponse order, BigDecimal canceledAmount) {
		return new AdminMemberRecentOrderResponse(order.id(), order.orderNumber(), order.status(),
				order.finalAmount(), canceledAmount, order.representativeProductName(), order.itemCount(),
				order.createdAt());
	}
}
