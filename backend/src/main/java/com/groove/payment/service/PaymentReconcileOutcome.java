package com.groove.payment.service;

import java.time.LocalDateTime;

import com.groove.payment.client.dto.RefundAccountInfo;

/**
 * 대사 적용 결과. 보상·취소 재시도·한정반 가상계좌 폐쇄가 필요하면 호출자가 트랜잭션 밖에서 토스를 호출한다.
 */
public record PaymentReconcileOutcome(
		boolean needsCompensation,
		boolean needsCancelRetry,
		boolean needsVirtualAccountClose,
		String paymentKey,
		LocalDateTime approvedAt,
		String idempotencyKey,
		RefundAccountInfo refundAccount
) {

	private static final PaymentReconcileOutcome APPLIED =
			new PaymentReconcileOutcome(false, false, false, null, null, null, null);

	public static PaymentReconcileOutcome alreadyResolved() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome applied() {
		return APPLIED;
	}

	public static PaymentReconcileOutcome needsCompensation(String paymentKey, LocalDateTime approvedAt) {
		return new PaymentReconcileOutcome(true, false, false, paymentKey, approvedAt, null, null);
	}

	public static PaymentReconcileOutcome needsCancelRetry(String paymentKey, String idempotencyKey,
			RefundAccountInfo refundAccount) {
		return new PaymentReconcileOutcome(false, true, false, paymentKey, null, idempotencyKey, refundAccount);
	}

	public static PaymentReconcileOutcome needsVirtualAccountClose(String paymentKey) {
		return new PaymentReconcileOutcome(false, false, true, paymentKey, null, null, null);
	}
}
