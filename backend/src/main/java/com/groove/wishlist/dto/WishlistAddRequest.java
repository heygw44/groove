package com.groove.wishlist.dto;

import jakarta.validation.constraints.NotNull;

public record WishlistAddRequest(
		@NotNull Long productId
) {
}
