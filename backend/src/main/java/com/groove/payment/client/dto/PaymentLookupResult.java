package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결제 조회 결과. PG 사에 종속되지 않도록 필요한 값만 추린다. */
public record PaymentLookupResult(
		PaymentLookupStatus status,
		String paymentKey,
		String method,
		BigDecimal totalAmount,
		LocalDateTime approvedAt,
		LocalDateTime canceledAt
) {

	public static PaymentLookupResult notFound() {
		return new PaymentLookupResult(PaymentLookupStatus.NOT_FOUND, null, null, null, null, null);
	}
}
