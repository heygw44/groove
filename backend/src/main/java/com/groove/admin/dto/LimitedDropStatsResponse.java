package com.groove.admin.dto;

import java.time.LocalDateTime;

import com.groove.limited.entity.LimitedDropStatus;

/**
 * 한정반 드롭 현황 통계 한 행. soldOutAt/soldOutSeconds 는 SOLD_OUT 상태가 아니면 null 이다.
 * attempts 는 시도 집계가 없으면 null 이고 {@code non_null} 설정에 따라 JSON 에서 통째로 생략되는데,
 * 이 기능 도입 이전에 이미 마감된 드롭이 여기 해당한다.
 */
public record LimitedDropStatsResponse(
		Long dropId,
		String productTitle,
		LimitedDropStatus status,
		int totalQuantity,
		int soldQuantity,
		double sellRate,
		LocalDateTime openAt,
		LocalDateTime closeAt,
		LocalDateTime soldOutAt,
		Long soldOutSeconds,
		LimitedDropAttemptStats attempts
) {

	public static LimitedDropStatsResponse of(LimitedDropStatsRow row, LimitedDropAttemptStats attempts) {
		return new LimitedDropStatsResponse(row.dropId(), row.productTitle(), row.status(), row.totalQuantity(),
				row.soldQuantity(), row.sellRate(), row.openAt(), row.closeAt(), row.soldOutAt(),
				row.soldOutSeconds(), attempts);
	}
}
