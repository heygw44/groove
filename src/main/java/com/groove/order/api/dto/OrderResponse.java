package com.groove.order.api.dto;

import com.groove.order.domain.Order;
import com.groove.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;

/**
 * 주문 응답 (API.md §3.5).
 *
 * <p>{@code shipping} 은 주문 시점에 캡처된 배송지 스냅샷이다(#W7-6). 운송장 번호·배송 진행 상태는
 * 별개의 {@code GET /shippings/{trackingNumber}} 가 다룬다 — 결제 상태(payment) 도 마찬가지로 별도.
 */
public record OrderResponse(
        String orderNumber,
        OrderStatus status,
        long totalAmount,
        List<OrderItemResponse> items,
        OrderShippingResponse shipping,
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
                OrderShippingResponse.from(order.getShippingInfo()),
                order.getCreatedAt());
    }
}
