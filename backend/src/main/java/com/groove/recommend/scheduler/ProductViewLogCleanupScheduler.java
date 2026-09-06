package com.groove.recommend.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.groove.recommend.service.ProductViewLogCleanupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 상품 조회 로그를 90일 보관 정책에 따라 매일 새벽에 배치 삭제한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductViewLogCleanupScheduler {

	static final int RETENTION_DAYS = 90;
	static final int BATCH_SIZE = 1000;
	static final int MAX_LOOPS = 100;

	private final ProductViewLogCleanupService productViewLogCleanupService;
	private final Clock clock;

	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void cleanUp() {
		LocalDateTime threshold = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
		try {
			int total = 0;
			for (int i = 0; i < MAX_LOOPS; i++) {
				int deleted = productViewLogCleanupService.deleteBatch(threshold, BATCH_SIZE);
				total += deleted;
				if (deleted < BATCH_SIZE) {
					break;
				}
			}
			if (total > 0) {
				log.info("상품 조회 로그 보관 기간 삭제 완료 threshold={} deleted={}", threshold, total);
			}
		} catch (RuntimeException e) {
			log.warn("상품 조회 로그 보관 기간 삭제 실패 threshold={}", threshold, e);
		}
	}
}
