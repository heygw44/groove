package com.groove.cart.api.dto;

import com.groove.cart.domain.Cart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * PATCH /api/v1/cart/items/{itemId} 요청 — 절대값으로 수량을 교체한다.
 */
public record CartItemPatchRequest(
        @Schema(description = "교체할 수량 (절대값, 1 이상 상한 내)", example = "3")
        @Min(1) @Max(Cart.MAX_ITEM_QUANTITY) int quantity
) {
}
