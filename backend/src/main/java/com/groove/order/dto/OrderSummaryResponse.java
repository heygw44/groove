package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.order.entity.OrderStatus;

public record OrderSummaryResponse(
		Long id,
		String orderNumber,
		OrderStatus status,
		BigDecimal finalAmount,
		BigDecimal discountAmount,
		String couponName,
		String representativeProductName,
		int itemCount,
		String thumbnailUrl,
		List<OrderListItemResponse> items,
		LocalDateTime createdAt
) {

	/** MyBatis 매퍼가 채우는 생성자. 상품 행은 매퍼 2차 조회 후 {@link #withItems} 로 붙인다. */
	public OrderSummaryResponse(Long id, String orderNumber, OrderStatus status, BigDecimal finalAmount,
			BigDecimal discountAmount, String couponName, String representativeProductName, int itemCount,
			String thumbnailUrl, LocalDateTime createdAt) {
		this(id, orderNumber, status, finalAmount, discountAmount, couponName, representativeProductName, itemCount,
				thumbnailUrl, List.of(), createdAt);
	}

	public OrderSummaryResponse withItems(List<OrderListItemResponse> items) {
		return new OrderSummaryResponse(id, orderNumber, status, finalAmount, discountAmount, couponName,
				representativeProductName, itemCount, thumbnailUrl, items, createdAt);
	}
}
