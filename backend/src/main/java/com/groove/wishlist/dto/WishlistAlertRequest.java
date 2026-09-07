package com.groove.wishlist.dto;

import jakarta.validation.constraints.NotNull;

public record WishlistAlertRequest(
		@NotNull Boolean alertEnabled
) {
}
