package com.groove.limited.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;

/** DB 커밋이 확정된 뒤에만 pending 선점 표시를 지운다. LimitedReleaseSynchronizer 와 같은 꼴이다. */
@Component
@RequiredArgsConstructor
public class LimitedPendingSynchronizer {

	private final LimitedDropRedisService limitedDropRedisService;

	public void clearAfterCommit(Long dropId, Long memberId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			limitedDropRedisService.confirm(dropId, memberId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				limitedDropRedisService.confirm(dropId, memberId);
			}
		});
	}
}
