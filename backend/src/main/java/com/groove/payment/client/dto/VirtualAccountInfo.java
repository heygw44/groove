package com.groove.payment.client.dto;

import java.time.LocalDateTime;

/** 토스 가상계좌 발급 정보. secret 은 저장 전 해시로만 남기고 응답엔 절대 노출하지 않는다. */
public record VirtualAccountInfo(
		String bankCode,
		String accountNumber,
		String customerName,
		LocalDateTime dueDate,
		String secret
) {
}
