package com.groove.cart.dto;

import com.groove.cart.entity.CartItem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemAddRequest(
		@NotNull Long productId,
		@NotNull @Min(1) @Max(CartItem.MAX_QUANTITY) Integer quantity
) {
}
