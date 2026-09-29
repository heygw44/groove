package com.groove.order.entity;

import java.util.List;

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
	RETURN_REJECT;

	private static final List<OrderItemClaimStatus> IN_PROGRESS = List.of(CANCEL_REQUEST, RETURN_REQUEST,
			COLLECTING);

	/** 발송·배송완료·구매확정 같은 이행 전이가 건너뛰어야 할 진행 중 클레임인지. null 은 클레임 없음이라 false. */
	public static boolean isInProgress(OrderItemClaimStatus status) {
		return status != null && IN_PROGRESS.contains(status);
	}
}
