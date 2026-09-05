package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;

public record AdminOrderDetailResponse(
		Long id,
		String orderNumber,
		Long memberId,
		String memberEmail,
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
		String cancelReason
) {

	public static AdminOrderDetailResponse from(Order order) {
		List<OrderItemResponse> items = order.getItems().stream()
				.map(OrderItemResponse::from)
				.toList();
		return new AdminOrderDetailResponse(order.getId(), order.getOrderNumber(), order.getMember().getId(),
				order.getMember().getEmail(), order.getStatus(), order.getTotalAmount(), order.getDiscountAmount(),
				order.getFinalAmount(), order.getCouponName(), items,
				ShippingAddressResponse.from(order.getShippingAddress()), order.getCreatedAt(), order.getExpiresAt(),
				order.getCanceledAt(), order.getCancelReason());
	}
}
