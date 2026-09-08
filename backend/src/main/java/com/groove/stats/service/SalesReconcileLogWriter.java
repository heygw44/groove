package com.groove.stats.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groove.stats.entity.SalesReconcileLog;
import com.groove.stats.repository.SalesReconcileLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대사 로그 기록/복구 마감을 별도 빈으로 분리한다. {@link SalesReconcileService} 가 이 메서드들을 자기
 * 트랜잭션에 걸어 호출하면 {@link SalesAggregationService#aggregateDate} 재계산 트랜잭션과 뒤섞여
 * "재계산을 커밋한 뒤 다시 읽기" 순서가 깨진다.
 */
@Component
@RequiredArgsConstructor
public class SalesReconcileLogWriter {

	private final SalesReconcileLogRepository salesReconcileLogRepository;

	@Transactional
	public List<SalesReconcileLog> saveAll(LocalDate saleDate, List<MetricDiff> diffs) {
		List<SalesReconcileLog> logs = diffs.stream()
				.map(diff -> SalesReconcileLog.of(saleDate, diff.metric(), diff.severity(), diff.expectedValue(),
						diff.actualValue()))
				.toList();
		return salesReconcileLogRepository.saveAll(logs);
	}

	@Transactional
	public void repair(List<Long> logIds) {
		if (logIds.isEmpty()) {
			return;
		}
		salesReconcileLogRepository.findAllById(logIds).forEach(SalesReconcileLog::repair);
	}
}
