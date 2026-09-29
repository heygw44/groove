package com.groove.order.entity;

/** 취소·반품 클레임 건(order_claim, V28) 자체의 상태. 이번 이슈에서는 테이블 없이 값만 정의한다. */
public enum OrderClaimStatus {

	REQUESTED,
	COLLECTING,
	DONE,
	REJECTED,
	WITHDRAWN
}
