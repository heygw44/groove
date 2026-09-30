package com.groove.order.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품주문 화면에서 구매자에게 보여줄 다음 동작을 계산한다. 이행 상태·클레임 상태·배송완료 시각·송장 유무만으로
 * 순수하게 판단하고(Clock 을 주입받지 않고 {@code now} 를 인자로 받는다), 결과 순서는 {@link OrderItemAction}
 * 선언 순서와 같다.
 */
public final class OrderItemActionPolicy {

	/** 배송완료 뒤 반품 요청이 가능한 기한(일). D7. */
	public static final int RETURN_PERIOD_DAYS = 7;

	private static final List<OrderItemClaimStatus> IN_PROGRESS_CLAIM_STATUSES = List.of(
			OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST,
			OrderItemClaimStatus.COLLECTING);

	// 수거가 시작되면(COLLECTING) 구매자가 철회할 수 없다.
	private static final List<OrderItemClaimStatus> WITHDRAWABLE_CLAIM_STATUSES = List.of(
			OrderItemClaimStatus.CANCEL_REQUEST, OrderItemClaimStatus.RETURN_REQUEST);

	private OrderItemActionPolicy() {
	}

	/**
	 * {@code refundInProgress} 는 클레임 환불이 토스에 나가 결과를 기다리는 중인지다. 이때 철회를 허용하면 환불이
	 * 확정돼도 되돌릴 클레임이 없어지므로 요청 철회를 내주지 않는다.
	 */
	public static List<OrderItemAction> resolve(OrderItemStatus status, OrderItemClaimStatus claimStatus,
			LocalDateTime deliveredAt, boolean hasTracking, boolean refundInProgress, LocalDateTime now) {
		boolean claimInProgress = isClaimInProgress(claimStatus);
		List<OrderItemAction> actions = new ArrayList<>();

		if (!claimInProgress && isCancelable(status)) {
			actions.add(OrderItemAction.CANCEL);
		}
		if (!claimInProgress && status == OrderItemStatus.PREPARING) {
			actions.add(OrderItemAction.CANCEL_REQUEST);
		}
		if (!claimInProgress && isReturnRequestable(status, deliveredAt, now)) {
			actions.add(OrderItemAction.RETURN_REQUEST);
		}
		if (!refundInProgress && claimStatus != null && WITHDRAWABLE_CLAIM_STATUSES.contains(claimStatus)) {
			actions.add(OrderItemAction.WITHDRAW_CLAIM);
		}
		if (hasTracking && isTrackable(status)) {
			actions.add(OrderItemAction.TRACK);
		}
		if (!claimInProgress && isConfirmable(status)) {
			actions.add(OrderItemAction.CONFIRM);
		}
		if (OrderItemStatus.REVIEWABLE.contains(status)) {
			actions.add(OrderItemAction.WRITE_REVIEW);
		}
		return actions;
	}

	private static boolean isClaimInProgress(OrderItemClaimStatus claimStatus) {
		return claimStatus != null && IN_PROGRESS_CLAIM_STATUSES.contains(claimStatus);
	}

	private static boolean isCancelable(OrderItemStatus status) {
		return status == OrderItemStatus.PAYMENT_WAITING || status == OrderItemStatus.PAID;
	}

	private static boolean isTrackable(OrderItemStatus status) {
		return status == OrderItemStatus.SHIPPING || status == OrderItemStatus.DELIVERED;
	}

	private static boolean isConfirmable(OrderItemStatus status) {
		return status == OrderItemStatus.SHIPPING || status == OrderItemStatus.DELIVERED;
	}

	/** 배송완료 시각을 기준으로 {@link #RETURN_PERIOD_DAYS}일 이내(경계 포함)까지 반품을 요청할 수 있다. */
	private static boolean isReturnRequestable(OrderItemStatus status, LocalDateTime deliveredAt,
			LocalDateTime now) {
		if (status != OrderItemStatus.DELIVERED || deliveredAt == null) {
			return false;
		}
		LocalDateTime deadline = deliveredAt.plusDays(RETURN_PERIOD_DAYS);
		return !now.isAfter(deadline);
	}
}
