package com.groove.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 주문 생성 요청의 단일 라인 (API.md §3.5).
 *
 * <p>quantity 상한 99 는 Cart.MAX_ITEM_QUANTITY 와 일치 — 동일 상품 1행 99개 한도.
 */
public record OrderItemRequest(
        @NotNull
        @Positive
        Long albumId,

        @Min(1)
        @Max(99)
        int quantity
) {
}
