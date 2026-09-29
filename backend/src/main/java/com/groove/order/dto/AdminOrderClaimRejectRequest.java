package com.groove.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminOrderClaimRejectRequest(
		@NotBlank @Size(max = 200) String rejectReason
) {
}
