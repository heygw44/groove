package com.groove.order.dto;

import com.groove.order.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusChangeRequest(
		@NotNull OrderStatus status
) {
}
