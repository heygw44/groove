package com.groove.cart.api.dto;

import com.groove.cart.domain.Cart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/cart/items 요청 — 동일 albumId 가 이미 담겨 있으면 quantity 누적, 없으면 신규 추가.
 *
 * <p>quantity 상한은 {@link Cart#MAX_ITEM_QUANTITY} 와 일치 — 도메인 상수가 단일 출처다.
 */
public record CartItemAddRequest(
        @Schema(description = "담을 앨범 ID", example = "1")
        @NotNull Long albumId,

        @Schema(description = "담을 수량 (1 이상, 상한 내). 동일 앨범이 있으면 누적", example = "2")
        @Min(1) @Max(Cart.MAX_ITEM_QUANTITY) int quantity
) {
}
