package com.groove.order.entity;

/** 주문 상태(결제 생애주기). "팔렸다" 판정은 상품주문 단위인 {@link OrderItemStatus#SOLD} 를 쓴다. */
public enum OrderStatus {

	PENDING,
	PAID,
	CANCELED
}
