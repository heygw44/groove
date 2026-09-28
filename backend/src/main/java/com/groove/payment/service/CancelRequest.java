package com.groove.payment.service;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.client.dto.RefundAccountInfo;

public record CancelRequest(
		Long orderId,
		Long paymentId,
		String paymentKey,
		String tossReason,
		OrderStatus previousOrderStatus,
		boolean alreadyRequested,
		RefundAccountInfo refundAccount
) {
}
