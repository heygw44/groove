package com.groove.limited.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** DB 커밋이 확정된 뒤에만 Redis 선점을 푼다. 롤백된 취소가 선점을 새어 나가게 하지 않기 위한 장치. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitedReleaseSynchronizer {

	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedRedisCircuitBreaker circuitBreaker;

	public void releaseAfterCommit(LimitedRelease release) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			release(release);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				release(release);
			}
		});
	}

	private void release(LimitedRelease release) {
		if (circuitBreaker.isOpen()) {
			circuitBreaker.noteFallback(release.dropId());
			log.debug("서킷 OPEN 이라 선점 해제를 건너뛴다, 복귀 시 재적재가 정리한다 dropId={} memberId={}",
					release.dropId(), release.memberId());
			return;
		}
		limitedDropRedisService.release(release.dropId(), release.memberId());
	}
}
