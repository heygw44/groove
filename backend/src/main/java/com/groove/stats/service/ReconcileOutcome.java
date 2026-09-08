package com.groove.stats.service;

import java.time.LocalDate;

/** 하루치 대사 실행 결과. unresolvedCriticalCount 는 재계산 1회로도 못 고친 CRITICAL 지표 수다. */
public record ReconcileOutcome(LocalDate saleDate, int mismatchCount, int repairedCount,
		int unresolvedCriticalCount) {

	static ReconcileOutcome clean(LocalDate saleDate) {
		return new ReconcileOutcome(saleDate, 0, 0, 0);
	}

	public boolean hasUnresolvedCritical() {
		return unresolvedCriticalCount > 0;
	}
}
