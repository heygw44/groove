package com.groove.payment.dto;

import java.time.LocalDateTime;

import com.groove.order.dto.OrderDetailResponse;
import com.groove.payment.entity.PaymentStatus;

public record PaymentCancelResponse(
		Long paymentId,
		Long orderId,
		String orderNumber,
		PaymentStatus status,
		LocalDateTime canceledAt
) {

	public static PaymentCancelResponse from(OrderDetailResponse order) {
		return new PaymentCancelResponse(order.payment().paymentId(), order.id(), order.orderNumber(),
				order.payment().status(), order.payment().canceledAt());
	}
}
