package com.groove.payment.dto;

import java.time.LocalDateTime;

import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public record PaymentCancelResponse(
		Long paymentId,
		Long orderId,
		String orderNumber,
		PaymentStatus status,
		LocalDateTime canceledAt
) {

	public static PaymentCancelResponse from(Payment payment) {
		return new PaymentCancelResponse(payment.getId(), payment.getOrder().getId(),
				payment.getOrder().getOrderNumber(), payment.getStatus(), payment.getCanceledAt());
	}
}
