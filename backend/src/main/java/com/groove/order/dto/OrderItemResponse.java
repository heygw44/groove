package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.groove.order.entity.CourierCode;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemAction;
import com.groove.order.entity.OrderItemActionPolicy;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

public record OrderItemResponse(
		Long productId,
		String productName,
		BigDecimal price,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl,
		String productOrderNumber,
		OrderItemStatus status,
		OrderItemClaimStatus claimStatus,
		BigDecimal paidAmount,
		CourierCode courierCode,
		String trackingNumber,
		LocalDateTime deliveredAt,
		List<OrderItemAction> availableActions
) {

	public static OrderItemResponse from(OrderItem item, String thumbnailUrl, LocalDateTime now) {
		boolean hasTracking = item.getTrackingNumber() != null;
		List<OrderItemAction> availableActions = OrderItemActionPolicy.resolve(item.getStatus(),
				item.getClaimStatus(), item.getDeliveredAt(), hasTracking, now);
		BigDecimal paidAmount = item.getLineAmount().subtract(item.getDiscountShare());
		return new OrderItemResponse(item.getProduct().getId(), item.getProductName(), item.getProductPrice(),
				item.getQuantity(), item.getLineAmount(), thumbnailUrl, item.getProductOrderNumber(),
				item.getStatus(), item.getClaimStatus(), paidAmount, item.getCourierCode(),
				item.getTrackingNumber(), item.getDeliveredAt(), availableActions);
	}

	/** 주문 상품 목록에 상품별 썸네일(상품 id 로 일괄 조회한 결과)을 붙인다. */
	public static List<OrderItemResponse> listFrom(List<OrderItem> items, Map<Long, String> thumbnailsByProductId,
			LocalDateTime now) {
		return items.stream()
				.map(item -> from(item, thumbnailsByProductId.get(item.getProduct().getId()), now))
				.toList();
	}
}
