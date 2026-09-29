package com.groove.order.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

/** 관리자 주문 상세에 노출하는 주문 상품 행. 구매자 응답과 달리 다음 동작(availableActions)은 내려주지 않는다. */
public record AdminOrderItemResponse(
		Long productId,
		String productName,
		BigDecimal price,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl,
		String productOrderNumber,
		OrderItemStatus status,
		OrderItemClaimStatus claimStatus
) {

	public static AdminOrderItemResponse from(OrderItem item, String thumbnailUrl) {
		return new AdminOrderItemResponse(item.getProduct().getId(), item.getProductName(), item.getProductPrice(),
				item.getQuantity(), item.getLineAmount(), thumbnailUrl, item.getProductOrderNumber(),
				item.getStatus(), item.getClaimStatus());
	}

	public static List<AdminOrderItemResponse> listFrom(List<OrderItem> items,
			Map<Long, String> thumbnailsByProductId) {
		return items.stream()
				.map(item -> from(item, thumbnailsByProductId.get(item.getProduct().getId())))
				.toList();
	}
}
