package com.groove.order.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public record OrderPaymentResponse(
		Long paymentId,
		String method,
		PaymentStatus status,
		BigDecimal amount,
		LocalDateTime approvedAt,
		LocalDateTime canceledAt
) {

	public static OrderPaymentResponse from(Payment payment) {
		return new OrderPaymentResponse(payment.getId(), payment.getMethod(), payment.getStatus(),
				payment.getAmount(), payment.getApprovedAt(), payment.getCanceledAt());
	}
}
