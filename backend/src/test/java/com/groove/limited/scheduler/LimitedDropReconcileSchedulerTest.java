package com.groove.limited.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropSyncService;
import com.groove.limited.service.LimitedReconcileLock;
import com.groove.limited.service.LimitedSyncResult;

@ExtendWith(MockitoExtension.class)
class LimitedDropReconcileSchedulerTest {

	@Mock
	private LimitedDropRepository limitedDropRepository;

	@Mock
	private LimitedDropSyncService limitedDropSyncService;

	@Mock
	private LimitedReconcileLock reconcileLock;

	@Mock
	private ShutdownSignal shutdownSignal;

	@Mock
	private AlertNotifier alertNotifier;

	private LimitedDropReconcileScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new LimitedDropReconcileScheduler(limitedDropRepository, limitedDropSyncService, reconcileLock,
				shutdownSignal, alertNotifier);
	}

	@Nested
	@DisplayName("reconcile()")
	class Reconcile {

		@Test
		@DisplayName("시작 시 셧다운 중이면 락도 잡지 않는다")
		void doesNotAcquireLockWhenShuttingDownAtStart() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(true);

			// when
			scheduler.reconcile();

			// then
			verify(reconcileLock, never()).runExclusively(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 대상 조회조차 하지 않는다")
		void doesNothingWhenLockAcquisitionFails() {
			// given
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(reconcileLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.reconcile();

			// then
			verify(limitedDropRepository, never()).findIdsByStatusIn(any());
		}

		@Test
		@DisplayName("락을 잡으면 OPEN/SOLD_OUT 드롭을 모두 대사한다")
		void syncsEveryOpenOrSoldOutDrop() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(limitedDropRepository.findIdsByStatusIn(List.of(LimitedDropStatus.OPEN, LimitedDropStatus.SOLD_OUT)))
					.willReturn(List.of(1L, 2L));
			given(limitedDropSyncService.sync(1L)).willReturn(Optional.of(new LimitedSyncResult(5, 5, false, 0, 0)));
			given(limitedDropSyncService.sync(2L)).willReturn(Optional.of(new LimitedSyncResult(5, 8, false, 1, 0)));

			// when
			scheduler.reconcile();

			// then
			verify(limitedDropSyncService).sync(1L);
			verify(limitedDropSyncService).sync(2L);
			verify(alertNotifier).notify(any(Alert.class));
		}

		@Test
		@DisplayName("한 건이 예외를 던져도 나머지 대상은 계속 처리한다")
		void continuesProcessingWhenOneDropFails() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false);
			given(limitedDropRepository.findIdsByStatusIn(any())).willReturn(List.of(1L, 2L));
			given(limitedDropSyncService.sync(1L)).willThrow(new RuntimeException("boom"));
			given(limitedDropSyncService.sync(2L)).willReturn(Optional.empty());

			// when
			scheduler.reconcile();

			// then
			verify(limitedDropSyncService).sync(1L);
			verify(limitedDropSyncService).sync(2L);
		}

		@Test
		@DisplayName("첫 건 처리 후 셧다운 신호가 오면 두 번째 건을 처리하지 않는다")
		void stopsProcessingWhenShutdownSignaledMidLoop() {
			// given
			stubLockToRunTask();
			given(shutdownSignal.isShuttingDown()).willReturn(false, false, true);
			given(limitedDropRepository.findIdsByStatusIn(any())).willReturn(List.of(1L, 2L));
			given(limitedDropSyncService.sync(1L)).willReturn(Optional.empty());

			// when
			scheduler.reconcile();

			// then
			verify(limitedDropSyncService).sync(1L);
			verify(limitedDropSyncService, never()).sync(2L);
		}
	}

	private void stubLockToRunTask() {
		given(reconcileLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}
}
