package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스 거래 조회(GET /v1/transactions) 응답 배열의 원소 한 건. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossTransactionResponse(
		String transactionKey,
		String paymentKey,
		String orderId,
		String method,
		String status,
		OffsetDateTime transactionAt,
		BigDecimal amount
) {
}
