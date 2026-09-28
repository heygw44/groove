package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 승인 결과. PG 사에 종속되지 않도록 필요한 값만 추린다. */
public record PaymentConfirmResult(
		String paymentKey,
		String orderId,
		String method,
		BigDecimal totalAmount,
		LocalDateTime approvedAt,
		PaymentLookupStatus status,
		String easyPayProvider,
		VirtualAccountInfo virtualAccount
) {

	/** 카드·간편결제 DONE 응답 전용. status 는 DONE 으로 고정된다. */
	public PaymentConfirmResult(String paymentKey, String orderId, String method, BigDecimal totalAmount,
			LocalDateTime approvedAt) {
		this(paymentKey, orderId, method, totalAmount, approvedAt, PaymentLookupStatus.DONE, null, null);
	}
}
