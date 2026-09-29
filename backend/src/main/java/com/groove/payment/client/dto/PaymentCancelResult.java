package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 취소 결과. transactionKey/balanceAmount 는 토스 취소 응답의 cancels[]/balanceAmount 에서 뽑는다.
 */
public record PaymentCancelResult(
		String paymentKey,
		String status,
		LocalDateTime canceledAt,
		String transactionKey,
		BigDecimal balanceAmount
) {

	/** transactionKey/balanceAmount 가 필요 없는 호출(기존 전액취소 테스트 등)을 위한 축약 팩토리. */
	public static PaymentCancelResult of(String paymentKey, String status, LocalDateTime canceledAt) {
		return new PaymentCancelResult(paymentKey, status, canceledAt, null, null);
	}
}
