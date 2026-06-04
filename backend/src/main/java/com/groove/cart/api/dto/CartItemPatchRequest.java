package com.groove.cart.api.dto;

import com.groove.cart.domain.Cart;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PATCH /api/v1/cart/items/{itemId} 요청 — 절대값으로 수량을 교체한다.
 */
public record CartItemPatchRequest(
        @Min(1) @Max(Cart.MAX_ITEM_QUANTITY) int quantity
) {
}
