package com.groove.order.dto;

import java.math.BigDecimal;

import com.groove.order.entity.OrderItem;

public record OrderItemResponse(
		Long productId,
		String productName,
		BigDecimal price,
		int quantity,
		BigDecimal lineAmount
) {

	public static OrderItemResponse from(OrderItem item) {
		return new OrderItemResponse(item.getProduct().getId(), item.getProductName(), item.getProductPrice(),
				item.getQuantity(), item.getLineAmount());
	}
}
