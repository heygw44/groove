package com.groove.notification.entity;

/** 알림 종류. */
public enum NotificationType {

	/** 위시리스트 상품이 재입고됐을 때. */
	RESTOCK,

	/** 위시리스트 상품 가격이 인하됐을 때. */
	PRICE_DROP,

	/** 구독 중인 앨범에 새 프레싱이 등록됐을 때. */
	NEW_PRESSING,

	/** 매출 대사에서 재계산으로도 못 고친 불일치가 발견됐을 때(관리자 전용). */
	STATS_MISMATCH
}
