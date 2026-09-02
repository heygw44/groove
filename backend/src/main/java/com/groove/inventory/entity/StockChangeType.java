package com.groove.inventory.entity;

/** 재고 변경 유형. CANCEL 은 주문 취소 전용이라 관리자 조정 API 에서는 거부한다. */
public enum StockChangeType {

	IN,
	OUT,
	CANCEL,
	ADJUST
}
