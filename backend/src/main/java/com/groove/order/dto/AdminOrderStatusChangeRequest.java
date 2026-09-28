package com.groove.order.dto;

import com.groove.order.entity.OrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusChangeRequest(
		@NotNull OrderStatus status,
		@Valid OrderCancelRequest.RefundAccount refundAccount
) {

	public AdminOrderStatusChangeRequest(OrderStatus status) {
		this(status, null);
	}
}
