package com.groove.order.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * 주문 응답 (API.md §3.5).
 *
 * <p>본 이슈(#43) 범위에서는 shipping/payment 는 응답에 포함하지 않는다 — 각각 W6-4/W6-5 에서 추가.
 */
public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        long totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt
) {

    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt());
    }
}
