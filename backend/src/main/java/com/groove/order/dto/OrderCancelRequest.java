package com.groove.order.dto;

import jakarta.validation.constraints.Size;

public record OrderCancelRequest(
		@Size(max = 200) String reason
) {
}
