package com.groove.payment.service;

import com.groove.order.entity.OrderStatus;

public record CancelRequest(
		Long orderId,
		Long paymentId,
		String paymentKey,
		String tossReason,
		OrderStatus previousOrderStatus,
		boolean alreadyRequested
) {
}
