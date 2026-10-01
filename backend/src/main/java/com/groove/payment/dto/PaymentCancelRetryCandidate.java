package com.groove.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결과불명으로 REQUESTED 에 남은 부분취소(payment_cancel) 재시도 후보 한 건. */
public record PaymentCancelRetryCandidate(
		Long paymentCancelId,
		Long paymentId,
		String paymentKey,
		String tossOrderId,
		BigDecimal cancelAmount,
		String idempotencyKey,
		String reason,
		LocalDateTime requestedAt,
		Long orderClaimId
) {
}
