package com.groove.order.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminOrderItemConfirmRequest(
		@NotEmpty @Size(max = AdminOrderItemBulkLimits.MAX_ITEMS) List<@NotNull Long> orderItemIds
) {
}
