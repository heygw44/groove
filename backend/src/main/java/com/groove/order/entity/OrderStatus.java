package com.groove.order.entity;

/** 주문 상태(결제 생애주기). "팔렸다" 판정은 상품주문 단위인 {@link OrderItemStatus#SOLD} 를 쓴다. */
public enum OrderStatus {

	PENDING,
	PAID,
	PREPARING,
	SHIPPED,
	DELIVERED,
	CANCELED,
	REFUNDED;

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
