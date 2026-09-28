package com.groove.payment.client.dto;

/** 가상계좌 결제 환불계좌. 토스 취소 API 의 refundReceiveAccount 로 전달한다. */
public record RefundAccountInfo(
		String bankCode,
		String accountNumber,
		String holderName
) {
}
