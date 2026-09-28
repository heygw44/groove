package com.groove.payment.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 입금 웹훅(DEPOSIT_CALLBACK) 본문. PAYMENT_STATUS_CHANGED 와 달리 eventType 도 data 래핑도 없는
 * 평평한 스키마라 {@link PaymentWebhookRequest} 와 별도로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentDepositCallbackRequest(
		OffsetDateTime createdAt,
		String secret,
		String status,
		String transactionKey,
		String orderId
) {
}
