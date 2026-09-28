package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;
import com.groove.payment.client.dto.RefundAccountInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

	private final OrderCancelWriter writer;
	private final PaidOrderCancelHook paidOrderCancelHook;
	private final PendingVirtualAccountCancelHook pendingVirtualAccountCancelHook;
	private final OrderService orderService;

	public OrderDetailResponse cancel(Long memberId, Long orderId, OrderCancelRequest request) {
		String reason = request == null ? null : request.reason();
		RefundAccountInfo refundAccount = toRefundAccount(request);
		OrderCancelTarget target = writer.findTarget(memberId, orderId);
		Long limitedDropId;
		if (target.requiresPaymentCancel()) {
			limitedDropId = paidOrderCancelHook.cancel(orderId, memberId, reason, refundAccount).limitedDropId();
		} else if (target.isWaitingForDeposit()) {
			limitedDropId = pendingVirtualAccountCancelHook.cancel(orderId, memberId, reason);
		} else {
			UnpaidCancelResult result = writer.cancelUnpaid(memberId, orderId, reason);
			limitedDropId = result.needsPaymentCancel()
					? paidOrderCancelHook.cancel(orderId, memberId, reason, refundAccount).limitedDropId()
					: result.limitedDropId();
		}
		return orderService.getDetail(memberId, orderId).withLimitedDropId(limitedDropId);
	}

	private RefundAccountInfo toRefundAccount(OrderCancelRequest request) {
		if (request == null || request.refundAccount() == null) {
			return null;
		}
		OrderCancelRequest.RefundAccount refundAccount = request.refundAccount();
		return new RefundAccountInfo(refundAccount.bankCode(), refundAccount.accountNumber(),
				refundAccount.holderName());
	}
}
