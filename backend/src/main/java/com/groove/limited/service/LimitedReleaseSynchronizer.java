package com.groove.limited.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/** DB 커밋이 확정된 뒤에만 Redis 선점을 푼다. 롤백된 취소가 선점을 새어 나가게 하지 않기 위한 장치. */
@Component
@RequiredArgsConstructor
public class LimitedReleaseSynchronizer {

	private final LimitedDropRedisService limitedDropRedisService;

	public void releaseAfterCommit(LimitedRelease release) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			limitedDropRedisService.release(release.dropId(), release.memberId());
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				limitedDropRedisService.release(release.dropId(), release.memberId());
			}
		});
	}
}
