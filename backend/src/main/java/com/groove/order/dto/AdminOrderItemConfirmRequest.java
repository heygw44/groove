package com.groove.order.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record AdminOrderItemConfirmRequest(
		@NotEmpty List<Long> orderItemIds
) {
}
