package com.groove.order.entity;

/** 취소·반품 클레임 건({@link OrderClaim}, V28) 자체의 상태. */
public enum OrderClaimStatus {

	REQUESTED,
	COLLECTING,
	DONE,
	REJECTED,
	WITHDRAWN
}
