package com.groove.cart.dto;

import java.math.BigDecimal;
import java.util.List;

import com.groove.cart.entity.Cart;

public record CartResponse(
		Long cartId,
		List<CartItemResponse> items,
		BigDecimal totalAmount
) {

	public static CartResponse of(Cart cart, List<CartItemResponse> items) {
		BigDecimal totalAmount = items.stream()
				.map(CartItemResponse::subtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CartResponse(cart.getId(), items, totalAmount);
	}
}
