package com.groove.order.entity;

/**
 * 상품주문(order_item)에 걸린 진행 중이거나 끝난 클레임을 파생 표시하는 값. null 이면 클레임이 없다는 뜻이다.
 * order_claim(클레임 건 자체의 이력)의 상태({@link OrderClaimStatus})와는 별개다.
 */
public enum OrderItemClaimStatus {

	CANCEL_REQUEST,
	CANCEL_DONE,
	CANCEL_REJECT,
	RETURN_REQUEST,
	COLLECTING,
	RETURN_DONE,
	RETURN_REJECT
}
