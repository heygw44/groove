package com.groove.payment.service;

import java.time.LocalDateTime;

/** {@link PaymentReconcileService#apply} 결과. needsCompensation 이면 트랜잭션 밖에서 토스 취소를 이어간다. */
public record PaymentReconcileOutcome(boolean needsCompensation, String paymentKey, LocalDateTime approvedAt) {

	private static final PaymentReconcileOutcome APPLIED = new PaymentReconcileOutcome(false, null, null);

	public static PaymentReconcileOutcome alreadyResolved() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome applied() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome needsCompensation(String paymentKey, LocalDateTime approvedAt) {
		return new PaymentReconcileOutcome(true, paymentKey, approvedAt);
	}
}
