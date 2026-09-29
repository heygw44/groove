package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스 Payment 객체. 승인과 취소가 같은 스키마를 돌려주므로 하나로 받는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
		String paymentKey,
		String orderId,
		String status,
		String method,
		BigDecimal totalAmount,
		BigDecimal balanceAmount,
		OffsetDateTime approvedAt,
		List<Cancel> cancels,
		EasyPay easyPay,
		VirtualAccount virtualAccount,
		String secret
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Cancel(String transactionKey, BigDecimal cancelAmount, OffsetDateTime canceledAt,
			String cancelStatus) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EasyPay(String provider) {
	}

	/** 입금 웹훅 검증용 secret 은 여기가 아니라 Payment 최상위 필드({@link #secret()})로 온다. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record VirtualAccount(String accountNumber, String bankCode, String customerName, OffsetDateTime dueDate) {
	}

	/** 부분 취소를 여러 번 하면 이력이 쌓이므로 마지막 취소를 이번 결과로 본다. */
	public Cancel lastCancel() {
		if (cancels == null || cancels.isEmpty()) {
			return null;
		}
		return cancels.get(cancels.size() - 1);
	}
}
