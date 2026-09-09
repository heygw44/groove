package com.groove.stats.scheduler;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.stats.service.AggregationLock;
import com.groove.stats.service.SalesAggregationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사전 집계 테이블을 재집계한다. 야간 배치가 최근 며칠을 통째로 덮어써 자기 치유하므로 Spring Batch 의
 * 재시작 지점 복원은 여기서 가치가 없다 — 그래서 평범한 스케줄러 + 트랜잭션 서비스로 구성한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesAggregationScheduler {

	static final int REAGGREGATE_WINDOW_DAYS = 7;

	private final SalesAggregationService salesAggregationService;
	private final AggregationLock aggregationLock;
	private final Clock clock;

	/** 최근 며칠의 취소/환불을 반영해 [D-7, D-1] 을 다시 덮어쓴다. */
	@Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
	public void reaggregateRecentDays() {
		LocalDate today = LocalDate.now(clock);
		LocalDate from = today.minusDays(REAGGREGATE_WINDOW_DAYS);
		LocalDate to = today.minusDays(1);
		runWindow("야간 재집계", from, to);
	}

	/** 15분 증분. "오차 허용 지표" 의 오차 상한을 정의한다 — 오늘 매출은 최대 15분 지연으로 정확해진다. */
	@Scheduled(cron = "0 */15 * * * *", zone = "Asia/Seoul")
	public void aggregateToday() {
		LocalDate today = LocalDate.now(clock);
		runWindow("오늘 증분", today, today);
	}

	private void runWindow(String label, LocalDate from, LocalDate to) {
		boolean acquired = aggregationLock.runExclusively(() -> aggregateRange(label, from, to));
		if (!acquired) {
			log.info("{} 락 획득 실패로 건너뛴다 from={} to={}", label, from, to);
		}
	}

	/**
	 * 스케줄러는 트랜잭션 밖에서 날짜를 돌고 날짜마다 서비스 트랜잭션을 새로 연다. 자기호출로는
	 * {@code @Transactional} 이 적용되지 않으므로 반드시 다른 빈({@link SalesAggregationService}) 을 호출한다.
	 * 날짜 하나가 실패해도 나머지 날짜는 계속 처리한다.
	 */
	private void aggregateRange(String label, LocalDate from, LocalDate to) {
		int success = 0;
		int failed = 0;
		long startedAt = System.currentTimeMillis();

		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			try {
				salesAggregationService.aggregateDate(date);
				success++;
			} catch (RuntimeException e) {
				failed++;
				log.warn("{} 실패 saleDate={}", label, date, e);
			}
		}

		long elapsedMs = System.currentTimeMillis() - startedAt;
		log.info("{} 완료 from={} to={} success={} failed={} elapsedMs={}", label, from, to, success, failed,
				elapsedMs);
	}
}
