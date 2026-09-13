package com.groove.payment.entity;

/** 대사 스케줄러가 결제 한 건에 내린 처리. */
public enum PaymentReconcileAction {
	APPROVED, CANCELED, FAILED, SKIPPED, MANUAL_REVIEW
}
