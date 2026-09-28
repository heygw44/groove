package com.groove.order.entity;

/** 주문 생성 경로. 장바구니 삭제 대상 판별(CART)과 통계 구분에 쓴다. */
public enum OrderSource {

	CART,
	DIRECT,
	LIMITED
}
