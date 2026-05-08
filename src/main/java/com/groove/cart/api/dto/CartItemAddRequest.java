package com.groove.cart.api.dto;

import com.groove.cart.domain.Cart;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/cart/items 요청 — 동일 albumId 가 이미 담겨 있으면 quantity 누적, 없으면 신규 추가.
 *
 * <p>quantity 상한은 {@link Cart#MAX_ITEM_QUANTITY} 와 일치 — 도메인 상수가 단일 출처다.
 */
public record CartItemAddRequest(
        @NotNull Long albumId,
        @Min(1) @Max(Cart.MAX_ITEM_QUANTITY) int quantity
) {
}
