package com.groove.order.dto;

import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCreateRequest(
		List<Long> cartItemIds,
		Long productId,
		@Positive Integer quantity,
		@NotNull Long addressId,
		Long memberCouponId
) {

	@AssertTrue(message = "cartItemIds 또는 productId+quantity 중 하나만 지정해야 합니다.")
	public boolean isSingleSource() {
		return isFromCart() ^ isDirect();
	}

	public boolean isFromCart() {
		return this.cartItemIds != null && !this.cartItemIds.isEmpty();
	}

	private boolean isDirect() {
		return this.productId != null && this.quantity != null;
	}
}
