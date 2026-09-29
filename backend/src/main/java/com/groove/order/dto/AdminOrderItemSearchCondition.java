package com.groove.order.dto;

import java.time.LocalDateTime;

import com.groove.order.entity.OrderStatusGroup;

/** {@link com.groove.order.mapper.OrderQueryMapper} 의 관리자 상품주문 목록 조회 조건. */
public record AdminOrderItemSearchCondition(
		OrderStatusGroup statusGroup,
		String keyword,
		LocalDateTime fromAt,
		LocalDateTime toExclusiveAt,
		int page,
		int size
) {

	public int offset() {
		return page * size;
	}
}
