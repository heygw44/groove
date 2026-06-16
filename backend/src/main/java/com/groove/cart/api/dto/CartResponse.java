package com.groove.cart.api.dto;

import com.groove.cart.domain.Cart;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 장바구니 전체 응답. 합계(totalAmount, totalItemCount)는 항목별 응답을 그대로 합산하며,
 * available=false 항목도 포함한다. cartId 는 비영속 시 nullable 이다.
 */
public record CartResponse(
        @Schema(description = "장바구니 ID (미영속 시 null 일 수 있음)", example = "1", nullable = true)
        Long cartId,

        @Schema(description = "장바구니 항목 목록")
        List<CartItemResponse> items,

        @Schema(description = "총 결제 예상 금액 (항목 소계 합계, 단위 원)", example = "37000")
        long totalAmount,

        @Schema(description = "총 항목 수량 합계", example = "3")
        int totalItemCount
) {

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();
        long totalAmount = items.stream().mapToLong(CartItemResponse::subtotal).sum();
        int totalItemCount = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(cart.getId(), items, totalAmount, totalItemCount);
    }
}
