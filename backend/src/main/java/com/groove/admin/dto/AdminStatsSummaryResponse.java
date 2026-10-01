package com.groove.admin.dto;

import java.math.BigDecimal;

/**
 * 관리자 대시보드 요약 카드. 처리 유형별 카운트 4개(발주확인 대기·입금대기·취소요청·반품요청)를 포함한다.
 */
public record AdminStatsSummaryResponse(
		BigDecimal todaySalesAmount,
		BigDecimal todayCancelAmount,
		long todayOrderCount,
		long todayNewMemberCount,
		long newOrderCount,
		long depositWaitingCount,
		long cancelRequestCount,
		long returnRequestCount
) {
}
