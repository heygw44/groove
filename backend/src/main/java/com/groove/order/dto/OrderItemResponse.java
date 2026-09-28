package com.groove.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.groove.order.entity.OrderItem;

public record OrderItemResponse(
		Long productId,
		String productName,
		BigDecimal price,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl
) {

	public static OrderItemResponse from(OrderItem item, String thumbnailUrl) {
		return new OrderItemResponse(item.getProduct().getId(), item.getProductName(), item.getProductPrice(),
				item.getQuantity(), item.getLineAmount(), thumbnailUrl);
	}

	/** 주문 상품 목록에 상품별 썸네일(상품 id 로 일괄 조회한 결과)을 붙인다. */
	public static List<OrderItemResponse> listFrom(List<OrderItem> items, Map<Long, String> thumbnailsByProductId) {
		return items.stream()
				.map(item -> from(item, thumbnailsByProductId.get(item.getProduct().getId())))
				.toList();
	}
}
