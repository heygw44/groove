package com.groove.order.entity;

import java.util.List;

/**
 * 구매자 주문 목록 탭(GET /orders?statusGroup=). 주문 안 상품주문 중 하나라도 그룹에 속하면 그 주문이 나온다.
 * {@code CANCEL_RETURN} 은 종결 상태(status)와 진행 중·취소/반품 완료 claim_status 를 포함하고,
 * 거부로 끝난 클레임(REJECT)은 제외한다.
 */
public enum OrderStatusGroup {

	PAYMENT_WAITING(List.of(OrderItemStatus.PAYMENT_WAITING), List.of()),
	PAID(List.of(OrderItemStatus.PAID), List.of()),
	PREPARING(List.of(OrderItemStatus.PREPARING), List.of()),
	SHIPPING(List.of(OrderItemStatus.SHIPPING), List.of()),
	DELIVERED(List.of(OrderItemStatus.DELIVERED), List.of()),
	PURCHASE_CONFIRMED(List.of(OrderItemStatus.PURCHASE_CONFIRMED), List.of()),
	CANCEL_RETURN(List.of(OrderItemStatus.CANCELED, OrderItemStatus.RETURNED, OrderItemStatus.CANCELED_BY_NOPAYMENT),
			List.of(OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST,
					OrderItemClaimStatus.COLLECTING, OrderItemClaimStatus.CANCEL_DONE,
					OrderItemClaimStatus.RETURN_DONE));

	private final List<OrderItemStatus> statuses;
	private final List<OrderItemClaimStatus> claimStatuses;

	OrderStatusGroup(List<OrderItemStatus> statuses, List<OrderItemClaimStatus> claimStatuses) {
		this.statuses = statuses;
		this.claimStatuses = claimStatuses;
	}

	public List<OrderItemStatus> getStatuses() {
		return statuses;
	}

	public List<OrderItemClaimStatus> getClaimStatuses() {
		return claimStatuses;
	}
}
