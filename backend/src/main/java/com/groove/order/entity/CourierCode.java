package com.groove.order.entity;

/** 발송처리 시 서버가 검증하는 택배사 코드. 조회 URL 템플릿은 서버가 모르고 프론트 couriers.ts 가 갖는다. */
public enum CourierCode {

	CJ,
	HANJIN,
	LOTTE,
	EPOST,
	LOGEN,
	KDEXP
}
