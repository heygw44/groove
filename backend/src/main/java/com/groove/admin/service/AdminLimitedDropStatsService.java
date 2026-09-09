package com.groove.admin.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.admin.dto.LimitedDropAttemptStats;
import com.groove.admin.dto.LimitedDropStatsRequest;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.LimitedDropStatsRow;
import com.groove.admin.mapper.AdminStatsMapper;
import com.groove.global.common.PageResponse;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropRedisService;

import lombok.RequiredArgsConstructor;

/** 한정반 드롭 시도 집계 조회. 진행 중(Redis)/종료(DB) 분기를 여기 한 곳에만 둔다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminLimitedDropStatsService {

	private final AdminStatsMapper adminStatsMapper;
	private final LimitedDropRedisService limitedDropRedisService;

	public PageResponse<LimitedDropStatsResponse> getLimitedDropStats(LimitedDropStatsRequest request) {
		long totalElements = adminStatsMapper.countLimitedDropStats();
		if (totalElements == 0) {
			return PageResponse.of(List.of(), request.resolvedPage(), request.resolvedSize(), 0);
		}
		List<LimitedDropStatsRow> rows = adminStatsMapper.findLimitedDropStats(request.offset(),
				request.resolvedSize());
		Map<Long, Map<LimitedAttemptResult, Long>> attemptsByDrop = fetchNonClosedAttempts(rows);
		List<LimitedDropStatsResponse> content = rows.stream()
				.map(row -> LimitedDropStatsResponse.of(row, resolveAttempts(row, attemptsByDrop)))
				.toList();
		return PageResponse.of(content, request.resolvedPage(), request.resolvedSize(), totalElements);
	}

	/** CLOSED 는 DB(limited_drop_stat) 값을 쓰므로 Redis 조회 대상에서 뺀다. */
	private Map<Long, Map<LimitedAttemptResult, Long>> fetchNonClosedAttempts(List<LimitedDropStatsRow> rows) {
		List<Long> nonClosedDropIds = rows.stream()
				.filter(row -> row.status() != LimitedDropStatus.CLOSED)
				.map(LimitedDropStatsRow::dropId)
				.toList();
		return limitedDropRedisService.getAttempts(nonClosedDropIds);
	}

	private LimitedDropAttemptStats resolveAttempts(LimitedDropStatsRow row,
			Map<Long, Map<LimitedAttemptResult, Long>> attemptsByDrop) {
		if (row.status() == LimitedDropStatus.CLOSED) {
			return toAttemptStats(row, row.soldOutCount(), row.alreadyPurchasedCount(), row.notOpenCount(),
					row.closedCount());
		}
		Map<LimitedAttemptResult, Long> attempts = attemptsByDrop.getOrDefault(row.dropId(), Map.of());
		return toAttemptStats(row, attempts.get(LimitedAttemptResult.SOLD_OUT),
				attempts.get(LimitedAttemptResult.ALREADY_PURCHASED), attempts.get(LimitedAttemptResult.NOT_OPEN),
				attempts.get(LimitedAttemptResult.CLOSED));
	}

	private LimitedDropAttemptStats toAttemptStats(LimitedDropStatsRow row, Long soldOutCount,
			Long alreadyPurchasedCount, Long notOpenCount, Long closedCount) {
		// 실패 집계가 전부 없으면(DB 행 없음 / Redis 빈 맵) 이 기능 이전 드롭과 구분이 안 되므로 성공 수와 무관하게 집계 없음으로 본다.
		if (soldOutCount == null && alreadyPurchasedCount == null && notOpenCount == null && closedCount == null) {
			return null;
		}
		long soldOut = orZero(soldOutCount);
		long alreadyPurchased = orZero(alreadyPurchasedCount);
		long notOpen = orZero(notOpenCount);
		long closed = orZero(closedCount);
		long successCount = row.soldQuantity();
		long attemptCount = successCount + soldOut + alreadyPurchased + notOpen + closed;
		double competitionRate = calculateCompetitionRate(attemptCount, row.totalQuantity());
		return new LimitedDropAttemptStats(attemptCount, successCount, soldOut, alreadyPurchased, notOpen, closed,
				competitionRate);
	}

	private double calculateCompetitionRate(long attemptCount, int totalQuantity) {
		if (totalQuantity == 0) {
			return 0.0;
		}
		double rate = (double) attemptCount / totalQuantity;
		return Math.round(rate * 10) / 10.0;
	}

	private static long orZero(Long value) {
		return value == null ? 0L : value;
	}
}
