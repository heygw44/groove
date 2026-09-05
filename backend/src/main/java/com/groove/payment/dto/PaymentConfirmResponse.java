package com.groove.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;

public record PaymentConfirmResponse(
		Long paymentId,
		Long orderId,
		String orderNumber,
		PaymentStatus status,
		String method,
		BigDecimal amount,
		LocalDateTime approvedAt
) {

	public static PaymentConfirmResponse from(Payment payment) {
		return new PaymentConfirmResponse(payment.getId(), payment.getOrder().getId(),
				payment.getOrder().getOrderNumber(), payment.getStatus(), payment.getMethod(), payment.getAmount(),
				payment.getApprovedAt());
	}
}
