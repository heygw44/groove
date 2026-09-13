package com.groove.payment.service;

import java.time.LocalDateTime;

/** {@link PaymentCompensator#cancelApproved} 결과. */
public record CompensationResult(boolean canceled, LocalDateTime canceledAt, String failureDetail) {

	public static CompensationResult canceled(LocalDateTime canceledAt) {
		return new CompensationResult(true, canceledAt, null);
	}

	public static CompensationResult notCanceled(String failureDetail) {
		return new CompensationResult(false, null, failureDetail);
	}
}
