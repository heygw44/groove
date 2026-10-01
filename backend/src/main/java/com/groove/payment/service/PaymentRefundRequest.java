package com.groove.payment.service;

import java.math.BigDecimal;

import com.groove.payment.client.dto.RefundAccountInfo;

/** T1(요청 기록)이 트랜잭션 밖 토스 호출에 넘기는 값. */
public record PaymentRefundRequest(
		Long paymentId,
		Long paymentCancelId,
		String paymentKey,
		BigDecimal cancelAmount,
		String reason,
		String idempotencyKey,
		RefundAccountInfo refundAccount,
		Long orderClaimId
) {
}
