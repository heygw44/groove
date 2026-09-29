package com.groove.admin.dto;

import java.math.BigDecimal;

/**
 * 관리자 대시보드 요약 카드. pendingOrderCount(결제 대기, 상품주문 상태 도입 전 정의)는 프론트가 아직 참조하고
 * 있어 남겨 두고, 처리 유형별 카운트 4개를 새로 더했다 - 프론트 교체 뒤 pendingOrderCount 는 제거한다.
 */
public record AdminStatsSummaryResponse(
		BigDecimal todaySalesAmount,
		BigDecimal todayCancelAmount,
		long todayOrderCount,
		long todayNewMemberCount,
		long pendingOrderCount,
		long newOrderCount,
		long depositWaitingCount,
		long cancelRequestCount,
		long returnRequestCount
) {
}
