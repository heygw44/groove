package com.groove.limited.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LimitedPendingSynchronizerTest {

	private static final Long DROP_ID = 1L;
	private static final Long MEMBER_ID = 10L;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	private LimitedPendingSynchronizer limitedPendingSynchronizer;

	@BeforeEach
	void setUp() {
		limitedPendingSynchronizer = new LimitedPendingSynchronizer(limitedDropRedisService);
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Nested
	@DisplayName("clearAfterCommit()")
	class ClearAfterCommit {

		@Test
		@DisplayName("활성 트랜잭션이 없으면 즉시 pending 을 정리한다")
		void clearsImmediatelyWithoutActiveTransaction() {
			// when
			limitedPendingSynchronizer.clearAfterCommit(DROP_ID, MEMBER_ID);

			// then
			verify(limitedDropRedisService).confirm(DROP_ID, MEMBER_ID);
		}

		@Test
		@DisplayName("트랜잭션이 활성 상태면 커밋 후에만 pending 을 정리한다")
		void clearsOnlyAfterCommitWhenTransactionActive() {
			// given
			TransactionSynchronizationManager.initSynchronization();

			// when
			limitedPendingSynchronizer.clearAfterCommit(DROP_ID, MEMBER_ID);

			// then
			verify(limitedDropRedisService, never()).confirm(DROP_ID, MEMBER_ID);

			// when: 커밋이 확정되면
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// then
			verify(limitedDropRedisService).confirm(DROP_ID, MEMBER_ID);
		}

		@Test
		@DisplayName("트랜잭션이 롤백되면 pending 을 정리하지 않는다")
		void doesNotClearWhenTransactionRollsBack() {
			// given
			TransactionSynchronizationManager.initSynchronization();
			limitedPendingSynchronizer.clearAfterCommit(DROP_ID, MEMBER_ID);

			// when
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

			// then
			verify(limitedDropRedisService, never()).confirm(DROP_ID, MEMBER_ID);
		}
	}
}
