package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.order.entity.OrderStatus;

public record AdminOrderSummaryResponse(
		Long id,
		String orderNumber,
		String memberEmail,
		OrderStatus status,
		BigDecimal finalAmount,
		int itemCount,
		LocalDateTime createdAt
) {
}
