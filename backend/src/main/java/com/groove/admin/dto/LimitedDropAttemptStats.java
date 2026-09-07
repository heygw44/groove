package com.groove.admin.dto;

/**
 * 한정반 드롭 시도 집계. competitionRate 는 "N:1 의 N"(배수)이고, 같은 응답의 sellRate(퍼센트)와는 단위가 다르다.
 * attemptCount 는 판정된 시도(성공 + 실패 4종)의 합이지 전체 요청 수가 아니다 — 배송지 없음·정지 회원 같은
 * 개별 요청 오류는 드롭 경쟁과 무관해 집계되지 않는다.
 */
public record LimitedDropAttemptStats(
		long attemptCount, long successCount, long soldOutCount,
		long alreadyPurchasedCount, long notOpenCount, long closedCount,
		double competitionRate) {
}
