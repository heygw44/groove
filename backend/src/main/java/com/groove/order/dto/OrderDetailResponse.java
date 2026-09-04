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
		String couponName,
		List<OrderItemResponse> items,
		ShippingAddressResponse shippingAddress,
		LocalDateTime createdAt,
		LocalDateTime expiresAt,
		LocalDateTime canceledAt,
		String cancelReason,
		Long limitedDropId
) {

	public static OrderDetailResponse from(Order order) {
		return from(order, null);
	}

	public static OrderDetailResponse from(Order order, Long limitedDropId) {
		List<OrderItemResponse> items = order.getItems().stream()
				.map(OrderItemResponse::from)
				.toList();
		return new OrderDetailResponse(order.getId(), order.getOrderNumber(), order.getStatus(),
				order.getTotalAmount(), order.getDiscountAmount(), order.getFinalAmount(), order.getCouponName(),
				items, ShippingAddressResponse.from(order.getShippingAddress()), order.getCreatedAt(),
				order.getExpiresAt(), order.getCanceledAt(), order.getCancelReason(), limitedDropId);
	}
}
