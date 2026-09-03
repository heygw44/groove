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
}
