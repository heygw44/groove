package com.groove.order.dto;

import java.time.LocalDateTime;

import com.groove.order.entity.OrderStatus;

/** {@link com.groove.order.mapper.OrderQueryMapper} 의 관리자 주문 목록 조회 조건. */
public record AdminOrderSearchCondition(
		OrderStatus status,
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
