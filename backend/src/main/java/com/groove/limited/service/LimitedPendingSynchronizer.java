package com.groove.limited.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** DB 커밋이 확정된 뒤에만 pending 선점 표시를 지운다. LimitedReleaseSynchronizer 와 같은 꼴이다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitedPendingSynchronizer {

	private final LimitedDropRedisService limitedDropRedisService;
	private final LimitedRedisCircuitBreaker circuitBreaker;

	public void clearAfterCommit(Long dropId, Long memberId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			confirm(dropId, memberId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				confirm(dropId, memberId);
			}
		});
	}

	private void confirm(Long dropId, Long memberId) {
		if (circuitBreaker.isOpen()) {
			circuitBreaker.noteFallback(dropId);
			log.debug("서킷 OPEN 이라 pending 정리를 건너뛴다, 복귀 시 재적재가 정리한다 dropId={} memberId={}", dropId, memberId);
			return;
		}
		limitedDropRedisService.confirm(dropId, memberId);
	}
}
