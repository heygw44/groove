package com.groove.order.api.dto;

import com.groove.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 항목 응답 (API.md §3.5).
 *
 * <p>가격 / 앨범명은 OrderItem 의 스냅샷을 그대로 노출한다 — Album 의 사후 변경과 무관.
 */
public record OrderItemResponse(
        @Schema(description = "앨범 ID", example = "1")
        Long albumId,
        @Schema(description = "주문 시점 앨범명 스냅샷", example = "Abbey Road")
        String albumTitle,
        @Schema(description = "단가(KRW)", example = "22500")
        long unitPrice,
        @Schema(description = "수량", example = "2")
        int quantity,
        @Schema(description = "소계(단가 × 수량, KRW)", example = "45000")
        long subtotal
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getAlbum().getId(),
                item.getAlbumTitleSnapshot(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal());
    }
}
