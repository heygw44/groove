package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.order.entity.CourierCode;
import com.groove.order.entity.OrderItemAction;
import com.groove.order.entity.OrderItemActionPolicy;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

/** {@code findItemsByOrderIds} 매퍼 프로젝션 전용. 페이지 내 주문 id 로 묶어 조회한 상품 행이다. */
public record OrderListItemRow(
		Long orderId,
		Long productId,
		String productName,
		int quantity,
		BigDecimal lineAmount,
		String thumbnailUrl,
		String productOrderNumber,
		OrderItemStatus status,
		OrderItemClaimStatus claimStatus,
		BigDecimal paidAmount,
		CourierCode courierCode,
		String trackingNumber,
		LocalDateTime deliveredAt
) {

	public OrderListItemResponse toResponse(LocalDateTime now) {
		boolean hasTracking = trackingNumber != null;
		List<OrderItemAction> availableActions = OrderItemActionPolicy.resolve(status, claimStatus, deliveredAt,
				hasTracking, now);
		return new OrderListItemResponse(productId, productName, quantity, lineAmount, thumbnailUrl,
				productOrderNumber, status, claimStatus, paidAmount, courierCode, trackingNumber, availableActions);
	}
}
