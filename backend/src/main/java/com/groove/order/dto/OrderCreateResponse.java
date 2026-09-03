package com.groove.order.dto;

import java.math.BigDecimal;

import com.groove.order.entity.Order;

public record OrderCreateResponse(
		Long orderId,
		String orderNumber,
		BigDecimal finalAmount
) {

	public static OrderCreateResponse from(Order order) {
		return new OrderCreateResponse(order.getId(), order.getOrderNumber(), order.getFinalAmount());
	}
}
