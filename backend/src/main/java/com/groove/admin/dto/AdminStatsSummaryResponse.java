package com.groove.admin.dto;

import java.math.BigDecimal;

/** 관리자 대시보드 요약 카드. */
public record AdminStatsSummaryResponse(
		BigDecimal todaySalesAmount,
		long todayOrderCount,
		long todayNewMemberCount,
		long pendingOrderCount
) {
}
