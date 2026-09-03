package com.groove.order.entity;

/** 주문 상태. */
public enum OrderStatus {

	PENDING,
	PAID,
	PREPARING,
	SHIPPED,
	DELIVERED,
	CANCELED,
	REFUNDED;

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
