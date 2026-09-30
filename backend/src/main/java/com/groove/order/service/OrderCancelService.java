package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.order.dto.OrderItemResponse;
import com.groove.payment.client.dto.RefundAccountInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

	private final OrderCancelWriter writer;
	private final PaidOrderCancelHook paidOrderCancelHook;
	private final PendingVirtualAccountCancelHook pendingVirtualAccountCancelHook;
	private final OrderItemClaimService orderItemClaimService;
	private final OrderService orderService;

	/**
	 * 취소 가능한 상품 전부를 취소한다. 결제 있는 주문 중 상품주문이 전부 PAID 이고 클레임 이력이 전혀 없으면
	 * (지금까지의 유일한 실제 경로) 기존 전액취소 경로를 그대로 쓴다 - 응답·멱등키·토스 호출 횟수가 바뀌지
	 * 않는다. 상품 하나가 이미 취소·반품됐거나(결제가 PARTIAL_CANCELED) PREPARING 이 섞여 있으면 상품 단위
	 * 클레임 경로로 남은 상품만 각각 처리한다.
	 */
	public OrderDetailResponse cancel(Long memberId, Long orderId, OrderCancelRequest request) {
		String reason = request == null ? null : request.reason();
		RefundAccountInfo refundAccount = toRefundAccount(request);
		OrderCancelTarget target = writer.findTarget(memberId, orderId);
		Long limitedDropId;
		if (target.requiresPaymentCancel()) {
			limitedDropId = cancelPaid(memberId, orderId, request, reason, refundAccount);
		} else if (target.isWaitingForDeposit()) {
			limitedDropId = pendingVirtualAccountCancelHook.cancel(orderId, memberId, reason);
		} else {
			UnpaidCancelResult result = writer.cancelUnpaid(memberId, orderId, reason);
			limitedDropId = result.needsPaymentCancel()
					? paidOrderCancelHook.cancel(orderId, memberId, reason, refundAccount).limitedDropId()
					: result.limitedDropId();
		}
		return orderService.getDetailAfterAction(memberId, orderId).withLimitedDropId(limitedDropId);
	}

	private Long cancelPaid(Long memberId, Long orderId, OrderCancelRequest request, String reason,
			RefundAccountInfo refundAccount) {
		OrderCancelPlan plan = writer.planCancel(memberId, orderId);
		if (plan.eligibleForFullCancel()) {
			return paidOrderCancelHook.cancel(orderId, memberId, reason, refundAccount).limitedDropId();
		}
		// 한 상품의 환불이 결과불명이면 그 결과가 확정될 때까지 같은 결제에 새 환불을 낼 수 없다. 나머지 상품은
		// 건드리지 않고 멈추며, 응답의 상품별 refundInProgress 로 어디서 멈췄는지 알린다.
		for (Long itemId : plan.cancelableItemIds()) {
			OrderItemResponse canceled = orderItemClaimService.cancel(memberId, orderId, itemId, request);
			if (canceled.refundInProgress()) {
				break;
			}
		}
		return null;
	}

	private RefundAccountInfo toRefundAccount(OrderCancelRequest request) {
		return OrderCancelRequest.RefundAccount.toInfo(request == null ? null : request.refundAccount());
	}
}
