package com.groove.recommend.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 상품 조회 로그 비동기 적재. 실패해도 상품 조회 응답에 영향을 주지 않도록 전부 삼킨다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewLogWriter {

	private final ProductViewLogSaver productViewLogSaver;
	private final RecentViewRedisService recentViewRedisService;

	@Async("viewLogExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(ProductViewedEvent event) {
		try {
			productViewLogSaver.save(event);
		} catch (RuntimeException e) {
			log.warn("상품 조회 로그 DB 저장 실패 memberId={} productId={}", event.memberId(), event.productId(), e);
		}

		// 비로그인 조회는 "회원별 최근 조회" 기능 대상이 아니라 recent-view:null 키가 생기면 안 된다.
		if (event.memberId() == null) {
			return;
		}
		try {
			recentViewRedisService.push(event.memberId(), event.productId());
		} catch (RuntimeException e) {
			log.warn("최근 조회 상품 Redis 적재 실패 memberId={} productId={}", event.memberId(), event.productId(), e);
		}
	}
}
