package com.groove.order.service;

import com.groove.order.entity.OrderStatus;

public record PaidOrderCancelResult(
		PaidOrderCancelStatus status,
		boolean alreadyRequested,
		OrderStatus previousOrderStatus,
		Long paymentId,
		Long limitedDropId
) {
}
