package com.groove.payment.service;

import java.time.LocalDateTime;

/** 대사 적용 결과. 보상 또는 취소 재시도가 필요하면 스케줄러가 트랜잭션 밖에서 토스를 호출한다. */
public record PaymentReconcileOutcome(
		boolean needsCompensation,
		boolean needsCancelRetry,
		String paymentKey,
		LocalDateTime approvedAt
) {

	private static final PaymentReconcileOutcome APPLIED = new PaymentReconcileOutcome(false, false, null, null);

	public static PaymentReconcileOutcome alreadyResolved() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome applied() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome needsCompensation(String paymentKey, LocalDateTime approvedAt) {
		return new PaymentReconcileOutcome(true, false, paymentKey, approvedAt);
	}

	public static PaymentReconcileOutcome needsCancelRetry(String paymentKey) {
		return new PaymentReconcileOutcome(false, true, paymentKey, null);
	}
}
