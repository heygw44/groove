package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;

public record OrderDetailResponse(
		Long id,
		String orderNumber,
		OrderStatus status,
		BigDecimal totalAmount,
		BigDecimal discountAmount,
		BigDecimal finalAmount,
		List<OrderItemResponse> items,
		ShippingAddressResponse shippingAddress,
		LocalDateTime createdAt,
		LocalDateTime expiresAt,
		LocalDateTime canceledAt,
		String cancelReason
) {

	public static OrderDetailResponse from(Order order) {
		List<OrderItemResponse> items = order.getItems().stream()
				.map(OrderItemResponse::from)
				.toList();
		return new OrderDetailResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
				order.getTotalAmount(), order.getDiscountAmount(), order.getFinalAmount(), items,
				ShippingAddressResponse.from(order.getShippingAddress()), order.getCreatedAt(), order.getExpiresAt(),
				order.getCanceledAt(), order.getCancelReason());
	}
}
