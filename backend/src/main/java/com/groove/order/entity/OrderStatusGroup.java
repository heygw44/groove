package com.groove.order.entity;

import java.util.List;

/**
 * 구매자 주문 목록 탭(GET /orders?statusGroup=). 주문 안 상품주문 중 하나라도 그룹에 속하면 그 주문이 나온다.
 * {@code CANCEL_RETURN} 은 종결 상태(status)뿐 아니라 진행 중인 클레임(claim_status IS NOT NULL)도 포함한다.
 */
public enum OrderStatusGroup {

	PAYMENT_WAITING(List.of(OrderItemStatus.PAYMENT_WAITING), false),
	PAID(List.of(OrderItemStatus.PAID), false),
	PREPARING(List.of(OrderItemStatus.PREPARING), false),
	SHIPPING(List.of(OrderItemStatus.SHIPPING), false),
	DELIVERED(List.of(OrderItemStatus.DELIVERED), false),
	PURCHASE_CONFIRMED(List.of(OrderItemStatus.PURCHASE_CONFIRMED), false),
	CANCEL_RETURN(List.of(OrderItemStatus.CANCELED, OrderItemStatus.RETURNED, OrderItemStatus.CANCELED_BY_NOPAYMENT),
			true);

	private final List<OrderItemStatus> statuses;
	private final boolean claimBased;

	OrderStatusGroup(List<OrderItemStatus> statuses, boolean claimBased) {
		this.statuses = statuses;
		this.claimBased = claimBased;
	}

	public List<OrderItemStatus> getStatuses() {
		return statuses;
	}

	public boolean isClaimBased() {
		return claimBased;
	}
}
