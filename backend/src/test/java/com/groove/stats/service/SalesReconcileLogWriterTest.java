package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesReconcileLog;
import com.groove.stats.repository.SalesReconcileLogRepository;

@ExtendWith(MockitoExtension.class)
class SalesReconcileLogWriterTest {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);

	@Mock
	private SalesReconcileLogRepository salesReconcileLogRepository;

	private SalesReconcileLogWriter writer;

	@BeforeEach
	void setUp() {
		writer = new SalesReconcileLogWriter(salesReconcileLogRepository);
	}

	@Nested
	@DisplayName("saveAll()")
	class SaveAll {

		@Test
		@DisplayName("지표별 불일치를 repaired=false 로그로 저장한다")
		void savesLogsForEachDiff() {
			// given
			MetricDiff diff = new MetricDiff(ReconcileMetric.DAILY_ORDER_COUNT, ReconcileSeverity.CRITICAL,
					BigDecimal.ONE, BigDecimal.TEN);
			given(salesReconcileLogRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

			// when
			List<SalesReconcileLog> saved = writer.saveAll(SALE_DATE, List.of(diff));

			// then
			assertThat(saved).hasSize(1);
			assertThat(saved.get(0).isRepaired()).isFalse();
			assertThat(saved.get(0).getMetric()).isEqualTo(ReconcileMetric.DAILY_ORDER_COUNT);
		}
	}

	@Nested
	@DisplayName("repair()")
	class Repair {

		@Test
		@DisplayName("빈 목록이면 조회 자체를 하지 않는다")
		void doesNothingWhenEmpty() {
			// when
			writer.repair(List.of());

			// then
			verify(salesReconcileLogRepository, never()).findAllById(anyList());
		}

		@Test
		@DisplayName("대상 로그를 repaired=true 로 닫는다")
		void repairsTargetLogs() {
			// given
			SalesReconcileLog log = SalesReconcileLog.of(SALE_DATE, ReconcileMetric.DAILY_ORDER_COUNT,
					ReconcileSeverity.CRITICAL, BigDecimal.ONE, BigDecimal.TEN);
			ReflectionTestUtils.setField(log, "id", 1L);
			given(salesReconcileLogRepository.findAllById(List.of(1L))).willReturn(List.of(log));

			// when
			writer.repair(List.of(1L));

			// then
			assertThat(log.isRepaired()).isTrue();
		}
	}
}
