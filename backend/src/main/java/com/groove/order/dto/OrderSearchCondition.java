package com.groove.order.dto;

import com.groove.order.entity.OrderStatus;

/** {@link com.groove.order.mapper.OrderQueryMapper} 에 전달되는 검색 조건. */
public record OrderSearchCondition(
		Long memberId,
		OrderStatus status,
		int page,
		int size
) {

	public int offset() {
		return page * size;
	}
}
