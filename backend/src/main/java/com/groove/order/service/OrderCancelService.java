package com.groove.order.service;

import org.springframework.stereotype.Service;

import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderDetailResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancelService {

	private final OrderCancelWriter writer;
	private final PaidOrderCancelHook paidOrderCancelHook;
	private final OrderService orderService;

	public OrderDetailResponse cancel(Long memberId, Long orderId, OrderCancelRequest request) {
		String reason = request == null ? null : request.reason();
		OrderCancelTarget target = writer.findTarget(memberId, orderId);
		Long limitedDropId;
		if (target.requiresPaymentCancel()) {
			limitedDropId = paidOrderCancelHook.cancel(orderId, memberId, reason).limitedDropId();
		} else {
			UnpaidCancelResult result = writer.cancelUnpaid(memberId, orderId, reason);
			limitedDropId = result.needsPaymentCancel()
					? paidOrderCancelHook.cancel(orderId, memberId, reason).limitedDropId()
					: result.limitedDropId();
		}
		return orderService.getDetail(memberId, orderId).withLimitedDropId(limitedDropId);
	}
}
