package com.groove.limited.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LimitedPurchaseResponse(
		Long orderId,
		String orderNumber,
		BigDecimal finalAmount,
		LocalDateTime expiresAt
) {
}
