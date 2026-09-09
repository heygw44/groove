package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesReconcileLog;

@ExtendWith(MockitoExtension.class)
class SalesReconcileServiceTest {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);

	@Mock
	private SalesReconcileComparator salesReconcileComparator;

	@Mock
	private SalesReconcileLogWriter salesReconcileLogWriter;

	@Mock
	private SalesAggregationService salesAggregationService;

	private SalesReconcileService service;

	@BeforeEach
	void setUp() {
		service = new SalesReconcileService(salesReconcileComparator, salesReconcileLogWriter,
				salesAggregationService);
	}

	private SalesReconcileLog logWithId(Long id, ReconcileMetric metric) {
		SalesReconcileLog log = SalesReconcileLog.of(SALE_DATE, metric, ReconcileSeverity.CRITICAL, BigDecimal.ONE,
				BigDecimal.TEN);
		ReflectionTestUtils.setField(log, "id", id);
		return log;
	}

	@Nested
	@DisplayName("reconcileDate()")
	class ReconcileDate {

		@Test
		@DisplayName("불일치가 없으면 아무것도 기록하지 않고 재계산도 호출하지 않는다")
		void doesNothingWhenNoDiff() {
			// given
			given(salesReconcileComparator.diff(SALE_DATE)).willReturn(List.of());

			// when
			ReconcileOutcome outcome = service.reconcileDate(SALE_DATE);

			// then
			assertThat(outcome.mismatchCount()).isZero();
			assertThat(outcome.hasUnresolvedCritical()).isFalse();
			verify(salesReconcileLogWriter, never()).saveAll(any(), anyList());
			verify(salesAggregationService, never()).aggregateDate(any());
		}

		@Test
		@DisplayName("재계산으로 복구되면 로그를 repaired 로 닫고 여전히 재계산은 1회만 호출한다")
		void repairsWhenRecalculationFixesMismatch() {
			// given
			MetricDiff initialDiff = new MetricDiff(ReconcileMetric.DAILY_ORDER_COUNT, ReconcileSeverity.CRITICAL,
					BigDecimal.ONE, BigDecimal.TEN);
			given(salesReconcileComparator.diff(SALE_DATE))
					.willReturn(List.of(initialDiff))
					.willReturn(List.of());
			given(salesReconcileLogWriter.saveAll(eq(SALE_DATE), anyList()))
					.willReturn(List.of(logWithId(1L, ReconcileMetric.DAILY_ORDER_COUNT)));

			// when
			ReconcileOutcome outcome = service.reconcileDate(SALE_DATE);

			// then
			verify(salesAggregationService, times(1)).aggregateDate(SALE_DATE);
			ArgumentCaptor<List<Long>> repairedIdsCaptor = ArgumentCaptor.forClass(List.class);
			verify(salesReconcileLogWriter).repair(repairedIdsCaptor.capture());
			assertThat(repairedIdsCaptor.getValue()).containsExactly(1L);
			assertThat(outcome.mismatchCount()).isEqualTo(1);
			assertThat(outcome.repairedCount()).isEqualTo(1);
			assertThat(outcome.hasUnresolvedCritical()).isFalse();
		}

		@Test
		@DisplayName("재계산 뒤에도 CRITICAL 불일치가 남으면 복구 처리 없이 미해결로 남긴다")
		void leavesUnresolvedWhenStillCriticalAfterRecalculation() {
			// given
			MetricDiff initialDiff = new MetricDiff(ReconcileMetric.DAILY_SALES_AMOUNT, ReconcileSeverity.CRITICAL,
					new BigDecimal("1000000"), new BigDecimal("1500000"));
			MetricDiff recheckDiff = new MetricDiff(ReconcileMetric.DAILY_SALES_AMOUNT, ReconcileSeverity.CRITICAL,
					new BigDecimal("1000000"), new BigDecimal("1500000"));
			given(salesReconcileComparator.diff(SALE_DATE))
					.willReturn(List.of(initialDiff))
					.willReturn(List.of(recheckDiff));
			given(salesReconcileLogWriter.saveAll(eq(SALE_DATE), anyList()))
					.willReturn(List.of(logWithId(2L, ReconcileMetric.DAILY_SALES_AMOUNT)));

			// when
			ReconcileOutcome outcome = service.reconcileDate(SALE_DATE);

			// then
			verify(salesAggregationService, times(1)).aggregateDate(SALE_DATE);
			verify(salesReconcileLogWriter).repair(List.of());
			assertThat(outcome.repairedCount()).isZero();
			assertThat(outcome.hasUnresolvedCritical()).isTrue();
			assertThat(outcome.unresolvedCriticalCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("재계산 뒤에도 남은 불일치가 WARN 뿐이면 미해결 CRITICAL 로 세지 않는다")
		void doesNotCountUnresolvedWarnAsCritical() {
			// given
			MetricDiff initialDiff = new MetricDiff(ReconcileMetric.DAILY_SALES_AMOUNT, ReconcileSeverity.WARN,
					new BigDecimal("1000000"), new BigDecimal("1000500"));
			MetricDiff recheckDiff = new MetricDiff(ReconcileMetric.DAILY_SALES_AMOUNT, ReconcileSeverity.WARN,
					new BigDecimal("1000000"), new BigDecimal("1000500"));
			given(salesReconcileComparator.diff(SALE_DATE))
					.willReturn(List.of(initialDiff))
					.willReturn(List.of(recheckDiff));
			given(salesReconcileLogWriter.saveAll(eq(SALE_DATE), anyList()))
					.willReturn(List.of(logWithId(3L, ReconcileMetric.DAILY_SALES_AMOUNT)));

			// when
			ReconcileOutcome outcome = service.reconcileDate(SALE_DATE);

			// then
			assertThat(outcome.hasUnresolvedCritical()).isFalse();
			verify(salesReconcileLogWriter).repair(List.of());
		}
	}
}
