package com.groove.stats.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesReconcileLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 원본과 사전 집계 테이블을 날짜 단위로 대사한다. 이 클래스에는 트랜잭션을 걸지 않는다 — 비교
 * ({@link SalesReconcileComparator}), 재계산({@link SalesAggregationService#aggregateDate}), 로그 기록
 * ({@link SalesReconcileLogWriter}) 을 각각 다른 빈의 짧은 트랜잭션으로 나눠야 재계산이 커밋된 뒤에야 재검증이
 * 그 결과를 읽는다. 이 메서드 자체에 트랜잭션을 걸면 재계산 쓰기가 여기 합류해 "재계산 후 다시 읽기"가 아니라
 * "같은 트랜잭션 안에서 아직 커밋 전인 값을 다시 읽기"가 돼버린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesReconcileService {

	private final SalesReconcileComparator salesReconcileComparator;
	private final SalesReconcileLogWriter salesReconcileLogWriter;
	private final SalesAggregationService salesAggregationService;

	public ReconcileOutcome reconcileDate(LocalDate saleDate) {
		List<MetricDiff> diffs = salesReconcileComparator.diff(saleDate);
		if (diffs.isEmpty()) {
			return ReconcileOutcome.clean(saleDate);
		}
		diffs.forEach(diff -> logMismatch("매출 대사 불일치 감지", saleDate, diff));

		Map<ReconcileMetric, Long> logIdByMetric = toLogIdByMetric(salesReconcileLogWriter.saveAll(saleDate, diffs));

		// 재계산이 못 고치는 건 데이터 문제가 아니라 집계 로직 버그다. 반복해도 소용없어 1회만 시도한다.
		salesAggregationService.aggregateDate(saleDate);

		Map<ReconcileMetric, MetricDiff> recheckByMetric = salesReconcileComparator.diff(saleDate).stream()
				.collect(Collectors.toMap(MetricDiff::metric, diff -> diff));

		List<Long> repairedIds = new ArrayList<>();
		List<MetricDiff> unresolved = new ArrayList<>();
		for (MetricDiff original : diffs) {
			MetricDiff stillMismatched = recheckByMetric.get(original.metric());
			if (stillMismatched == null) {
				repairedIds.add(logIdByMetric.get(original.metric()));
			} else {
				unresolved.add(stillMismatched);
			}
		}
		salesReconcileLogWriter.repair(repairedIds);

		List<MetricDiff> unresolvedCritical = unresolved.stream()
				.filter(diff -> diff.severity() == ReconcileSeverity.CRITICAL)
				.toList();
		unresolvedCritical.forEach(diff -> logMismatch("매출 대사 자동 복구 실패", saleDate, diff));

		return new ReconcileOutcome(saleDate, diffs.size(), repairedIds.size(), unresolvedCritical.size());
	}

	private void logMismatch(String message, LocalDate saleDate, MetricDiff diff) {
		log.error("{} saleDate={} metric={} severity={} expected={} actual={}", message, saleDate, diff.metric(),
				diff.severity(), diff.expectedValue(), diff.actualValue());
	}

	private Map<ReconcileMetric, Long> toLogIdByMetric(List<SalesReconcileLog> logs) {
		Map<ReconcileMetric, Long> result = new HashMap<>();
		for (SalesReconcileLog savedLog : logs) {
			result.put(savedLog.getMetric(), savedLog.getId());
		}
		return result;
	}
}
