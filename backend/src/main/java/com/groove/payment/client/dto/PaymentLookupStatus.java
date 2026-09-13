package com.groove.payment.client.dto;

/** 토스 결제 조회 상태. NOT_FOUND 는 토스에 해당 결제가 없음을 우리 쪽에서 표현한 값이다. */
public enum PaymentLookupStatus {
	READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED, NOT_FOUND
}
