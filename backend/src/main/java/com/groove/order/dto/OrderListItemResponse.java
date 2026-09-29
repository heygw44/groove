package com.groove.order.dto;

import java.math.BigDecimal;
import java.util.List;

import com.groove.order.entity.CourierCode;
import com.groove.order.entity.OrderItemAction;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;

/** 주문 목록 화면에 노출하는 주문 상품 행. */
public record OrderListItemResponse(
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
		List<OrderItemAction> availableActions
) {
}
