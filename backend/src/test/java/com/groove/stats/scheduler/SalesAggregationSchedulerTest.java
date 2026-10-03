package com.groove.stats.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.support.CronExpression;

import com.groove.stats.service.AggregationLock;
import com.groove.stats.service.SalesAggregationService;

@ExtendWith(MockitoExtension.class)
class SalesAggregationSchedulerTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private SalesAggregationService salesAggregationService;

	@Mock
	private AggregationLock aggregationLock;

	private SalesAggregationScheduler scheduler;

	private Clock clock;

	@BeforeEach
	void setUp() {
		// KST 2031-03-16 03:30
		clock = Clock.fixed(Instant.parse("2031-03-15T18:30:00Z"), ZONE);
		scheduler = new SalesAggregationScheduler(salesAggregationService, aggregationLock, clock);
	}

	private void stubLockRunsTask() {
		given(aggregationLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}

	@Nested
	@DisplayName("reaggregateRecentDays()")
	class ReaggregateRecentDays {

		@Test
		@DisplayName("D-7 부터 D-1 까지 정확히 7일을 재집계한다")
		void aggregatesExactWindow() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);

			// when
			scheduler.reaggregateRecentDays();

			// then
			ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
			verify(salesAggregationService, times(SalesAggregationScheduler.REAGGREGATE_WINDOW_DAYS))
					.aggregateDate(dateCaptor.capture());
			assertThat(dateCaptor.getAllValues())
					.containsExactly(today.minusDays(7), today.minusDays(6), today.minusDays(5), today.minusDays(4),
							today.minusDays(3), today.minusDays(2), today.minusDays(1));
		}

		@Test
		@DisplayName("한 날짜가 예외를 던져도 나머지 날짜는 계속 처리한다")
		void continuesWhenOneDateFails() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);
			doAnswer(invocation -> {
				throw new RuntimeException("boom");
			}).when(salesAggregationService).aggregateDate(today.minusDays(4));

			// when
			scheduler.reaggregateRecentDays();

			// then
			verify(salesAggregationService, times(SalesAggregationScheduler.REAGGREGATE_WINDOW_DAYS))
					.aggregateDate(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 서비스를 아예 호출하지 않는다")
		void doesNotCallServiceWhenLockNotAcquired() {
			// given
			given(aggregationLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.reaggregateRecentDays();

			// then
			verify(salesAggregationService, never()).aggregateDate(any());
		}
	}

	@Nested
	@DisplayName("aggregateToday()")
	class AggregateToday {

		@Test
		@DisplayName("오늘 하루만 집계한다")
		void aggregatesOnlyToday() {
			// given
			stubLockRunsTask();
			LocalDate today = LocalDate.now(clock);

			// when
			scheduler.aggregateToday();

			// then
			verify(salesAggregationService, times(1)).aggregateDate(today);
		}

		@Test
		@DisplayName("락 획득에 실패하면 서비스를 아예 호출하지 않는다")
		void doesNotCallServiceWhenLockNotAcquired() {
			// given
			given(aggregationLock.runExclusively(any())).willReturn(false);

			// when
			scheduler.aggregateToday();

			// then
			verify(salesAggregationService, never()).aggregateDate(any());
		}

		@Test
		@DisplayName("15분 증분은 야간 재집계·대사와 같은 시각에 시작하지 않는다")
		void neverStartsWithNightlyOrReconcileJobs() {
			// given
			ZonedDateTime dayStart = ZonedDateTime.of(2031, 3, 15, 0, 0, 0, 0, ZONE);

			// when
			List<ZonedDateTime> todayFires = fireTimesOn(SalesAggregationScheduler.TODAY_CRON, dayStart);
			List<ZonedDateTime> nightlyFires = fireTimesOn(SalesAggregationScheduler.NIGHTLY_CRON, dayStart);
			List<ZonedDateTime> reconcileFires = fireTimesOn(SalesReconcileScheduler.RECONCILE_CRON, dayStart);

			// then
			assertThat(todayFires).hasSize(96);
			assertThat(nightlyFires).hasSize(1);
			assertThat(reconcileFires).hasSize(1);
			assertThat(todayFires).doesNotContainAnyElementsOf(nightlyFires)
					.doesNotContainAnyElementsOf(reconcileFires);
		}

		private List<ZonedDateTime> fireTimesOn(String cron, ZonedDateTime dayStart) {
			CronExpression expression = CronExpression.parse(cron);
			ZonedDateTime dayEnd = dayStart.plusDays(1);
			List<ZonedDateTime> fires = new ArrayList<>();
			ZonedDateTime next = expression.next(dayStart.minusSeconds(1));
			while (next != null && next.isBefore(dayEnd)) {
				fires.add(next);
				next = expression.next(next);
			}
			return fires;
		}
	}
}
