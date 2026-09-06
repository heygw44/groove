package com.groove.recommend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.recommend.service.BoughtTogetherAggregator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 공동구매 집계를 1시간마다 갱신해 Redis 에 적재한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoughtTogetherScheduler {

	private final BoughtTogetherAggregator boughtTogetherAggregator;

	@Scheduled(fixedDelay = 3_600_000L, initialDelay = 30_000L)
	public void refresh() {
		try {
			int productCount = boughtTogetherAggregator.refresh();
			log.info("공동구매 집계 스케줄러 실행 완료 productCount={}", productCount);
		} catch (RuntimeException e) {
			log.warn("공동구매 집계 스케줄러 실행 실패", e);
		}
	}
}
