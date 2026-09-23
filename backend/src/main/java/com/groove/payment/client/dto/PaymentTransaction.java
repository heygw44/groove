package com.groove.payment.client.dto;

import java.time.LocalDateTime;

/**
 * 토스 거래 조회(/v1/transactions) 결과 한 건. status 는 모르는 값도 그대로 보존한다 — 새 상태 문자열이
 * 추가돼도 하루치 대조 자체가 실패하면 안 된다.
 */
public record PaymentTransaction(
		String transactionKey,
		String paymentKey,
		String tossOrderId,
		String status,
		LocalDateTime transactionAt
) {
}
