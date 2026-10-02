package com.groove.payment.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 조회 결과. PG 사에 종속되지 않도록 필요한 값만 추린다. balanceAmount/lastCancelTransactionKey 는
 * 부분취소 대사가 취소 잔액을 맞춰보고 마지막 취소 거래를 식별하는 데 쓴다 - 기존 호출부는 6-인자
 * 생성자를 그대로 쓸 수 있게 남겨 둔다.
 */
public record PaymentLookupResult(
		PaymentLookupStatus status,
		String paymentKey,
		String method,
		BigDecimal totalAmount,
		LocalDateTime approvedAt,
		LocalDateTime canceledAt,
		BigDecimal balanceAmount,
		String lastCancelTransactionKey,
		String easyPayProvider,
		VirtualAccountInfo virtualAccount
) {

	/** 간편결제 사업자·가상계좌 정보가 필요 없는 호출을 위한 생성자. */
	public PaymentLookupResult(PaymentLookupStatus status, String paymentKey, String method, BigDecimal totalAmount,
			LocalDateTime approvedAt, LocalDateTime canceledAt, BigDecimal balanceAmount,
			String lastCancelTransactionKey) {
		this(status, paymentKey, method, totalAmount, approvedAt, canceledAt, balanceAmount,
				lastCancelTransactionKey, null, null);
	}

	/** 취소 잔액 대사가 필요 없는 기존 호출(승인·일반 조회 흐름)을 위한 축약 생성자. */
	public PaymentLookupResult(PaymentLookupStatus status, String paymentKey, String method, BigDecimal totalAmount,
			LocalDateTime approvedAt, LocalDateTime canceledAt) {
		this(status, paymentKey, method, totalAmount, approvedAt, canceledAt, null, null, null, null);
	}

	public static PaymentLookupResult notFound() {
		return new PaymentLookupResult(PaymentLookupStatus.NOT_FOUND, null, null, null, null, null, null, null, null,
				null);
	}
}
