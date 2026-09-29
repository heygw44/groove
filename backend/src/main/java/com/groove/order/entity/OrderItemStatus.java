package com.groove.order.entity;

import java.util.List;

/** 상품주문(order_item) 단위 이행 상태. Order.status(결제 생애주기)와 분리해 상품마다 다른 단계를 가질 수 있게 한다. */
public enum OrderItemStatus {

	PAYMENT_PENDING,
	PAYMENT_WAITING,
	PAID,
	PREPARING,
	SHIPPING,
	DELIVERED,
	PURCHASE_CONFIRMED,
	CANCELED,
	CANCELED_BY_NOPAYMENT,
	RETURNED;

	/** 결제가 끝나 실제 판매로 보는 상태. 판매량·추천·리뷰 자격 등 "팔렸다" 판정의 기준. */
	public static final List<OrderItemStatus> SOLD = List.of(PAID, PREPARING, SHIPPING, DELIVERED,
			PURCHASE_CONFIRMED);

	/** 리뷰 작성 자격 기준. 구매확정 필수화 전까지는 배송완료만으로도 작성할 수 있다. */
	public static final List<OrderItemStatus> REVIEWABLE = List.of(DELIVERED, PURCHASE_CONFIRMED);
}
