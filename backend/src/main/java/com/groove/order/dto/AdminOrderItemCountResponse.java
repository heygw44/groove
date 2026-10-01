package com.groove.order.dto;

/** 관리자 상품주문 건수. 목록과 별도로 조회한다. */
public record AdminOrderItemCountResponse(
		long totalElements
) {
}
