package com.groove.order.entity;

/** 상품주문 화면에서 구매자가 다음으로 할 수 있는 동작. {@link OrderItemActionPolicy} 가 계산한다. */
public enum OrderItemAction {

	CANCEL,
	CANCEL_REQUEST,
	RETURN_REQUEST,
	WITHDRAW_CLAIM,
	TRACK,
	CONFIRM,
	WRITE_REVIEW
}
