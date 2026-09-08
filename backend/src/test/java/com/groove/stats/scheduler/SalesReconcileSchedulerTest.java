package com.groove.stats.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.stats.service.AggregationLock;
import com.groove.stats.service.ReconcileOutcome;
import com.groove.stats.service.SalesReconcileService;
import com.groove.stats.service.StatsAlertDispatcher;

@ExtendWith(MockitoExtension.class)
class SalesReconcileSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private SalesReconcileService salesReconcileService;

	@Mock
	private StatsAlertDispatcher statsAlertDispatcher;

	@Mock
	private AggregationLock aggregationLock;

	private SalesReconcileScheduler scheduler;

	private Clock clock;

	@BeforeEach
	void setUp() {
		// KST 2031-03-16 05:00
		clock = Clock.fixed(Instant.parse("2031-03-15T20:00:00Z"), ZONE);
		scheduler = new SalesReconcileScheduler(salesReconcileService, statsAlertDispatcher, aggregationLock, clock);
	}

	private void stubLockRunsTask() {
		given(aggregationLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}

	@Nested
	@DisplayName("reconcileRecentDays()")
	class ReconcileRecentDays {

		@Test
		@DisplayName("D-35 부터 D-1 까지 정확히 35일을 대사한다")
		void reconcilesExactWindow() {
			// given
			stubLockRunsTask();
			given(salesReconcileService.reconcileDate(any())).willReturn(
					new ReconcileOutcome(LocalDate.now(clock), 0, 0, 0));

			// when
			scheduler.reconcileRecentDays();

			// then
			verify(salesReconcileService, times(SalesReconcileScheduler.RECONCILE_WINDOW_DAYS))
					.reconcileDate(any());
		}

		@Test
		@DisplayName("한 날짜가 예외를 던져도 나머지 날짜는 계속 대사한다")
		void continuesWhenOneDateFails() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);
			given(salesReconcileService.reconcileDate(any())).willReturn(new ReconcileOutcome(today, 0, 0, 0));
			doAnswer(invocation -> {
				throw new RuntimeException("boom");
			}).when(salesReconcileService).reconcileDate(today.minusDays(10));

			// when
			scheduler.reconcileRecentDays();

			// then
			verify(salesReconcileService, times(SalesReconcileScheduler.RECONCILE_WINDOW_DAYS))
					.reconcileDate(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 대사 서비스를 아예 호출하지 않는다")
		void doesNotCallServiceWhenLockNotAcquired() {
			// given
			given(aggregationLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.reconcileRecentDays();

			// then
			verify(salesReconcileService, never()).reconcileDate(any());
			verify(statsAlertDispatcher, never()).dispatchMismatchSummary(any(), any(), anyInt());
		}

		@Test
		@DisplayName("미해결 CRITICAL 이 있는 날짜가 하나라도 있으면 실행 요약 알림을 한 번만 보낸다")
		void dispatchesOneSummaryAlertWhenUnresolvedCriticalExists() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);
			given(salesReconcileService.reconcileDate(any())).willReturn(new ReconcileOutcome(today, 0, 0, 0));
			given(salesReconcileService.reconcileDate(today.minusDays(5)))
					.willReturn(new ReconcileOutcome(today.minusDays(5), 1, 0, 1));
			given(salesReconcileService.reconcileDate(today.minusDays(20)))
					.willReturn(new ReconcileOutcome(today.minusDays(20), 1, 0, 1));

			// when
			scheduler.reconcileRecentDays();

			// then
			ArgumentCaptor<Integer> unresolvedCaptor = ArgumentCaptor.forClass(Integer.class);
			verify(statsAlertDispatcher, times(1)).dispatchMismatchSummary(any(), any(), unresolvedCaptor.capture());
			org.assertj.core.api.Assertions.assertThat(unresolvedCaptor.getValue()).isEqualTo(2);
		}

		@Test
		@DisplayName("미해결 CRITICAL 이 없으면 알림을 보내지 않는다")
		void doesNotDispatchAlertWhenNoUnresolvedCritical() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);
			given(salesReconcileService.reconcileDate(any())).willReturn(new ReconcileOutcome(today, 0, 0, 0));

			// when
			scheduler.reconcileRecentDays();

			// then
			verify(statsAlertDispatcher, never()).dispatchMismatchSummary(any(), any(), anyInt());
		}
	}
}
