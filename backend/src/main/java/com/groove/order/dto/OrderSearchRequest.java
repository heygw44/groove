package com.groove.order.dto;

import com.groove.order.entity.OrderStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderSearchRequest(
		OrderStatus status,
		@PositiveOrZero Integer page,
		@Min(1) @Max(100) Integer size
) {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;

	public OrderSearchCondition toCondition(Long memberId) {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		return new OrderSearchCondition(memberId, status, resolvedPage, resolvedSize);
	}
}
