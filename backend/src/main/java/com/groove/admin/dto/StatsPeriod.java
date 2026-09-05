package com.groove.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

/** 통계 조회 기간. 기본값 보정과 범위 검증은 {@link #resolve}에서 한 번에 처리한다. */
public record StatsPeriod(LocalDate from, LocalDate to) {

	private static final int DEFAULT_WINDOW_DAYS = 30;
	private static final int MAX_PERIOD_DAYS = 365;

	public static StatsPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
		LocalDate resolvedTo = to == null ? today : to;
		LocalDate resolvedFrom = from == null ? resolvedTo.minusDays(DEFAULT_WINDOW_DAYS - 1) : from;

		if (resolvedFrom.isAfter(resolvedTo) || ChronoUnit.DAYS.between(resolvedFrom, resolvedTo) >= MAX_PERIOD_DAYS) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}

		return new StatsPeriod(resolvedFrom, resolvedTo);
	}

	public LocalDateTime fromAt() {
		return this.from.atStartOfDay();
	}

	public LocalDateTime toExclusiveAt() {
		return this.to.plusDays(1).atStartOfDay();
	}

	public List<LocalDate> dates() {
		List<LocalDate> result = new ArrayList<>();
		for (LocalDate date = this.from; !date.isAfter(this.to); date = date.plusDays(1)) {
			result.add(date);
		}
		return result;
	}
}
