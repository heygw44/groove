package com.groove.order.entity;

import java.util.List;

/** 주문 상태. */
public enum OrderStatus {

	PENDING,
	PAID,
	PREPARING,
	SHIPPED,
	DELIVERED,
	CANCELED,
	REFUNDED;

	/** 결제가 끝나 실제 구매로 보는 상태. 통계·추천의 "PAID 이상" 기준. */
	public static final List<OrderStatus> PAID_OR_LATER = List.of(PAID, PREPARING, SHIPPED, DELIVERED);

	public boolean isCancelable() {
		return this == PENDING || this == PAID;
	}

	/** 관리자 상태 전이(PATCH /admin/orders/{id}/status)에서 허용되는 전이만 true. */
	public boolean canTransitionTo(OrderStatus next) {
		return switch (this) {
			case PAID -> next == PREPARING || next == CANCELED;
			case PREPARING -> next == SHIPPED || next == CANCELED;
			case SHIPPED -> next == DELIVERED;
			default -> false;
		};
	}
}
