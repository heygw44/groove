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
		BigDecimal canceledAmount,
		LocalDateTime approvedAt,
		LocalDateTime canceledAt,
		String easyPayProvider,
		VirtualAccountResponse virtualAccount
) {

	public OrderPaymentResponse(Long paymentId, String method, PaymentStatus status, BigDecimal amount,
			LocalDateTime approvedAt, LocalDateTime canceledAt) {
		this(paymentId, method, status, amount, BigDecimal.ZERO, approvedAt, canceledAt, null,
				null);
	}

	public static OrderPaymentResponse from(Payment payment) {
		return new OrderPaymentResponse(payment.getId(), payment.getMethod(), payment.getStatus(),
				payment.getAmount(), payment.getCanceledAmount(), payment.getApprovedAt(), payment.getCanceledAt(),
				payment.getEasyPayProvider(), VirtualAccountResponse.from(payment));
	}

	/** 가상계좌 결제가 아니면 null 이다. secret 은 절대 담지 않는다. */
	public record VirtualAccountResponse(String bankCode, String accountNumber, String customerName,
			LocalDateTime dueDate) {

		static VirtualAccountResponse from(Payment payment) {
			if (!payment.isVirtualAccount()) {
				return null;
			}
			return new VirtualAccountResponse(payment.getVaBankCode(), payment.getVaAccountNumber(),
					payment.getVaCustomerName(), payment.getVaDueDate());
		}
	}
}
