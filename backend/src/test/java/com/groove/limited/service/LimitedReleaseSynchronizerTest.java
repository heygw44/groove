package com.groove.limited.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class LimitedReleaseSynchronizerTest {

	private static final Long DROP_ID = 1L;
	private static final Long MEMBER_ID = 10L;

	@Mock
	private LimitedDropRedisService limitedDropRedisService;

	@Mock
	private LimitedRedisCircuitBreaker circuitBreaker;

	private LimitedReleaseSynchronizer limitedReleaseSynchronizer;

	@BeforeEach
	void setUp() {
		limitedReleaseSynchronizer = new LimitedReleaseSynchronizer(limitedDropRedisService, circuitBreaker);
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Nested
	@DisplayName("releaseAfterCommit()")
	class ReleaseAfterCommit {

		@Test
		@DisplayName("활성 트랜잭션이 없으면 즉시 선점을 해제한다")
		void releasesImmediatelyWithoutActiveTransaction() {
			// given
			LimitedRelease release = new LimitedRelease(DROP_ID, MEMBER_ID);

			// when
			limitedReleaseSynchronizer.releaseAfterCommit(release);

			// then
			verify(limitedDropRedisService).release(DROP_ID, MEMBER_ID);
		}

		@Test
		@DisplayName("트랜잭션이 활성 상태면 커밋 후에만 선점을 해제한다")
		void releasesOnlyAfterCommitWhenTransactionActive() {
			// given
			LimitedRelease release = new LimitedRelease(DROP_ID, MEMBER_ID);
			TransactionSynchronizationManager.initSynchronization();

			// when
			limitedReleaseSynchronizer.releaseAfterCommit(release);

			// then
			verify(limitedDropRedisService, never()).release(DROP_ID, MEMBER_ID);

			// when: 커밋이 확정되면
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// then
			verify(limitedDropRedisService).release(DROP_ID, MEMBER_ID);
		}

		@Test
		@DisplayName("트랜잭션이 롤백되면 선점을 해제하지 않는다")
		void doesNotReleaseWhenTransactionRollsBack() {
			// given
			LimitedRelease release = new LimitedRelease(DROP_ID, MEMBER_ID);
			TransactionSynchronizationManager.initSynchronization();
			limitedReleaseSynchronizer.releaseAfterCommit(release);

			// when
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

			// then
			verify(limitedDropRedisService, never()).release(DROP_ID, MEMBER_ID);
		}

		@Test
		@DisplayName("서킷이 OPEN 이면 커밋 후에도 선점 해제를 건너뛰고 fallbackDrops 에 표시한다")
		void skipsReleaseAndNotesFallbackWhenCircuitOpen() {
			// given
			LimitedRelease release = new LimitedRelease(DROP_ID, MEMBER_ID);
			when(circuitBreaker.isOpen()).thenReturn(true);
			TransactionSynchronizationManager.initSynchronization();
			limitedReleaseSynchronizer.releaseAfterCommit(release);

			// when
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// then
			verify(limitedDropRedisService, never()).release(DROP_ID, MEMBER_ID);
			verify(circuitBreaker).noteFallback(DROP_ID);
		}

		@Test
		@DisplayName("서킷이 HALF_OPEN(=isOpen false) 이면 커밋 후 선점을 해제한다")
		void releasesWhenCircuitHalfOpen() {
			// given
			LimitedRelease release = new LimitedRelease(DROP_ID, MEMBER_ID);
			when(circuitBreaker.isOpen()).thenReturn(false);
			TransactionSynchronizationManager.initSynchronization();
			limitedReleaseSynchronizer.releaseAfterCommit(release);

			// when
			TransactionSynchronizationManager.getSynchronizations()
					.forEach(TransactionSynchronization::afterCommit);

			// then
			verify(limitedDropRedisService).release(DROP_ID, MEMBER_ID);
		}
	}
}
