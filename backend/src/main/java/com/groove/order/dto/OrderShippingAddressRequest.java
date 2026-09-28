package com.groove.order.dto;

import jakarta.validation.constraints.NotNull;

public record OrderShippingAddressRequest(
		@NotNull Long addressId
) {
}
