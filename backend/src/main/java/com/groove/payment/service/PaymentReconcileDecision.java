package com.groove.payment.service;

/** {@link PaymentReconcileRule#decide} 의 결과. */
public enum PaymentReconcileDecision {
	APPROVE, COMPENSATE, COMPLETE_CANCEL, RETRY_CANCEL, FAIL, SYNC_CANCELED, ISSUE_VIRTUAL_ACCOUNT, SKIP, MANUAL_REVIEW
}
