package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.order.entity.OrderStatus;

public record OrderSummaryResponse(
		Long id,
		String orderNumber,
		OrderStatus status,
		BigDecimal finalAmount,
		String representativeProductName,
		int itemCount,
		String thumbnailUrl,
		LocalDateTime createdAt
) {
}
