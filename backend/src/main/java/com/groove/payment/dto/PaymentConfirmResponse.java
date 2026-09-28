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
		LocalDateTime approvedAt,
		String easyPayProvider,
		VirtualAccountResponse virtualAccount
) {

	public PaymentConfirmResponse(Long paymentId, Long orderId, String orderNumber, PaymentStatus status,
			String method, BigDecimal amount, LocalDateTime approvedAt) {
		this(paymentId, orderId, orderNumber, status, method, amount, approvedAt, null, null);
	}

	public static PaymentConfirmResponse from(Payment payment) {
		return new PaymentConfirmResponse(payment.getId(), payment.getOrder().getId(),
				payment.getOrder().getOrderNumber(), payment.getStatus(), payment.getMethod(), payment.getAmount(),
				payment.getApprovedAt(), payment.getEasyPayProvider(), VirtualAccountResponse.from(payment));
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
