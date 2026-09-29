package com.groove.order.dto;

import jakarta.validation.constraints.NotNull;

public record AdminOrderClaimCompleteRequest(
		@NotNull Boolean restock
) {
}
