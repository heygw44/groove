package com.groove.stats.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.stats.service.AggregationLock;
import com.groove.stats.service.ReconcileOutcome;
import com.groove.stats.service.SalesReconcileService;
import com.groove.stats.service.StatsAlertDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 최근 {@value #RECONCILE_WINDOW_DAYS}일을 날짜 단위로 대사한다. 야간 재집계(03:30) 결과를 검증해야 하므로
 * 그 뒤인 05:00 에 돈다. 재집계와 같은 {@link AggregationLock} 을 공유해 동시 실행을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesReconcileScheduler {

	static final int RECONCILE_WINDOW_DAYS = 35;

	private final SalesReconcileService salesReconcileService;
	private final StatsAlertDispatcher statsAlertDispatcher;
	private final AggregationLock aggregationLock;
	private final Clock clock;

	@Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
	public void reconcileRecentDays() {
		boolean acquired = aggregationLock.runExclusively(this::runReconcile);
		if (!acquired) {
			log.info("대사 락 획득 실패로 건너뛴다");
		}
	}

	private void runReconcile() {
		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(RECONCILE_WINDOW_DAYS);
		LocalDate to = today.minusDays(1);

		int totalMismatch = 0;
		int totalRepaired = 0;
		int unresolvedDates = 0;
		int failedDates = 0;

		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			try {
				ReconcileOutcome outcome = salesReconcileService.reconcileDate(date);
				totalMismatch += outcome.mismatchCount();
				totalRepaired += outcome.repairedCount();
				if (outcome.hasUnresolvedCritical()) {
					unresolvedDates++;
				}
			} catch (RuntimeException e) {
				failedDates++;
				log.warn("대사 실패 saleDate={}", date, e);
			}
		}

		if (unresolvedDates > 0) {
			dispatchAlert(from, to, unresolvedDates);
		}

		log.info("대사 완료 from={} to={} mismatch={} repaired={} unresolvedDates={} failed={}", from, to,
				totalMismatch, totalRepaired, unresolvedDates, failedDates);
	}

	// 알림 적재 실패가 이미 끝난 대사 결과(로그 기록·자동 복구)를 되돌리면 안 되므로 별도로 감싼다.
	private void dispatchAlert(LocalDate from, LocalDate to, int unresolvedDates) {
		try {
			statsAlertDispatcher.dispatchMismatchSummary(from, to, unresolvedDates);
		} catch (RuntimeException e) {
			log.warn("대사 불일치 알림 적재 실패", e);
		}
	}
}
