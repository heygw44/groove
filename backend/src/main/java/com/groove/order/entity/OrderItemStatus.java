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

	/** 결제가 끝나 실제 판매로 보는 상태. 판매량·추천 등 "팔렸다" 판정의 기준. */
	public static final List<OrderItemStatus> SOLD = List.of(PAID, PREPARING, SHIPPING, DELIVERED,
			PURCHASE_CONFIRMED);

	/** 리뷰 작성 자격 기준. 배송완료만으로는 쓸 수 없고 구매확정까지 필요하다. */
	public static final List<OrderItemStatus> REVIEWABLE = List.of(PURCHASE_CONFIRMED);

	/** 리뷰 작성 자격 미달이지만 구매확정만 하면 되는 상태(배송중·배송완료, eligibility 이유 코드 구분용). */
	public static final List<OrderItemStatus> AWAITING_PURCHASE_CONFIRM = List.of(SHIPPING, DELIVERED);

	/**
	 * 취소·반품·미입금취소로 끝난 상태(D5). 쿠폰 복원과 주문 취소 확정 판단에 쓴다. 구매확정(PURCHASE_CONFIRMED)은
	 * 성공적으로 끝난 상태라 여기 포함하지 않는다 - 한 주문 안에서 어떤 상품은 구매확정, 다른 상품은 취소인 채로
	 * 섞일 수 있어 "전부 끝났다"의 기준이 서로 다르다.
	 */
	public static final List<OrderItemStatus> CANCEL_TERMINAL = List.of(CANCELED, CANCELED_BY_NOPAYMENT, RETURNED);
}
