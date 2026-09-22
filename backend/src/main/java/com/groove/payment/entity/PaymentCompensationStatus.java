package com.groove.payment.entity;

/** payment 행이 없는 보상 취소 대기 큐의 상태. */
public enum PaymentCompensationStatus {
	PENDING, CANCELED, MANUAL_REVIEW
}
