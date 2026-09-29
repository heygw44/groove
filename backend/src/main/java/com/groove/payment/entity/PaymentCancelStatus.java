package com.groove.payment.entity;

/**
 * 결제 취소 건({@link PaymentCancel}) 상태. 결제 전체의 {@link PaymentStatus} 와 별개로 취소 건 하나하나의
 * 진행 상태를 추적한다.
 */
public enum PaymentCancelStatus {
	REQUESTED, DONE, UNKNOWN, FAILED, MANUAL_REVIEW
}
