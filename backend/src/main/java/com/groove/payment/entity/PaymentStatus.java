package com.groove.payment.entity;

/** 결제 상태. 승인 전 READY 로 만들어지고 토스 승인/취소 결과에 따라 전이한다. */
public enum PaymentStatus {
	READY, DONE, CANCELED, FAILED
}
