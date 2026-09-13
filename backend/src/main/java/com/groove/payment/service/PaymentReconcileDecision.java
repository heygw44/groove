package com.groove.payment.service;

/** {@link PaymentReconcileRule#decide} 의 결과. */
public enum PaymentReconcileDecision {
	APPROVE, COMPENSATE, FAIL, SYNC_CANCELED, SKIP, MANUAL_REVIEW
}
