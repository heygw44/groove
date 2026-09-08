package com.groove.stats.entity;

/** 대사 시 발견된 차이의 심각도. */
public enum ReconcileSeverity {

	/** 반올림 등으로 설명되는 사소한 차이. */
	WARN,

	/** 원본과 집계값이 실질적으로 어긋난 경우. */
	CRITICAL
}
