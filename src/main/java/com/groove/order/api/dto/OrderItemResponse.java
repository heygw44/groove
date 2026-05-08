package com.groove.order.api.dto;

import com.groove.order.domain.OrderItem;

/**
 * 주문 항목 응답 (API.md §3.5).
 *
 * <p>가격 / 앨범명은 OrderItem 의 스냅샷을 그대로 노출한다 — Album 의 사후 변경과 무관.
 */
public record OrderItemResponse(
        Long albumId,
        String albumTitle,
        long unitPrice,
        int quantity,
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
