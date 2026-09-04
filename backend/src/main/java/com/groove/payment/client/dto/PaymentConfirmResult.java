package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 승인 결과. PG 사에 종속되지 않도록 필요한 값만 추린다. */
public record PaymentConfirmResult(
		String paymentKey,
		String orderId,
		String method,
		BigDecimal totalAmount,
		LocalDateTime approvedAt
) {
}
