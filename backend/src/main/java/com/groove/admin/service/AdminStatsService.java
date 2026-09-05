package com.groove.admin.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductSortType;
import com.groove.admin.dto.PopularProductStatsCondition;
import com.groove.admin.dto.PopularProductStatsRequest;
import com.groove.admin.dto.StatsPeriod;
import com.groove.admin.dto.StatsPeriodRequest;
import com.groove.admin.mapper.AdminStatsMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;

/** 관리자 대시보드 통계 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminStatsService {

	private static final int DEFAULT_LIMIT = 10;
	private static final int MAX_LIMIT = 100;

	private final AdminStatsMapper adminStatsMapper;
	private final Clock clock;

	public List<DailySalesResponse> getDailySales(StatsPeriodRequest request) {
		StatsPeriod period = StatsPeriod.resolve(request.from(), request.to(), LocalDate.now(clock));
		List<DailySalesResponse> rows = adminStatsMapper.findDailySales(period.fromAt(), period.toExclusiveAt());
		Map<LocalDate, DailySalesResponse> rowsByDate = rows.stream()
				.collect(Collectors.toMap(DailySalesResponse::date, Function.identity()));

		return period.dates().stream()
				.map(date -> rowsByDate.getOrDefault(date, DailySalesResponse.empty(date)))
				.toList();
	}

	public List<PopularProductResponse> getPopularProducts(PopularProductStatsRequest request) {
		StatsPeriod period = StatsPeriod.resolve(request.from(), request.to(), LocalDate.now(clock));
		int limit = resolveLimit(request.limit());
		PopularProductSortType sort = PopularProductSortType.from(request.sort());
		PopularProductStatsCondition condition = new PopularProductStatsCondition(period.fromAt(),
				period.toExclusiveAt(), limit, sort);

		return adminStatsMapper.findPopularProducts(condition);
	}

	public List<LimitedDropStatsResponse> getLimitedDropStats() {
		return adminStatsMapper.findLimitedDropStats();
	}

	public AdminStatsSummaryResponse getSummary() {
		LocalDate today = LocalDate.now(clock);
		return adminStatsMapper.findSummary(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
	}

	private int resolveLimit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		return limit;
	}
}
