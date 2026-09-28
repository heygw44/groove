package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.order.entity.Order;

public record OrderCreateResponse(
		Long orderId,
		String orderNumber,
		BigDecimal totalAmount,
		BigDecimal discountAmount,
		BigDecimal finalAmount,
		String couponName,
		LocalDateTime expiresAt
) {

	public static OrderCreateResponse from(Order order) {
		return new OrderCreateResponse(order.getId(), order.getOrderNumber(), order.getTotalAmount(),
				order.getDiscountAmount(), order.getFinalAmount(), order.getCouponName(), order.getExpiresAt());
	}
}
