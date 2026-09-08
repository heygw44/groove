package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.groove.global.common.PageResponse;
import com.groove.stats.dto.ReconcileLogResponse;
import com.groove.stats.dto.ReconcileLogSearchRequest;
import com.groove.stats.entity.ReconcileMetric;
import com.groove.stats.entity.ReconcileSeverity;
import com.groove.stats.entity.SalesReconcileLog;
import com.groove.stats.repository.SalesReconcileLogRepository;

@ExtendWith(MockitoExtension.class)
class SalesReconcileLogQueryServiceTest {

	private static final LocalDate SALE_DATE = LocalDate.of(2031, 3, 15);

	@Mock
	private SalesReconcileLogRepository salesReconcileLogRepository;

	private SalesReconcileLogQueryService service;

	@BeforeEach
	void setUp() {
		service = new SalesReconcileLogQueryService(salesReconcileLogRepository);
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("repaired 파라미터가 없으면 전체를 조회한다")
		void queriesAllWhenRepairedIsNull() {
			// given
			SalesReconcileLog log = SalesReconcileLog.of(SALE_DATE, ReconcileMetric.DAILY_ORDER_COUNT,
					ReconcileSeverity.CRITICAL, BigDecimal.ONE, BigDecimal.TEN);
			Page<SalesReconcileLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1);
			given(salesReconcileLogRepository.findAll(any(Pageable.class))).willReturn(page);

			// when
			PageResponse<ReconcileLogResponse> response = service.getList(
					new ReconcileLogSearchRequest(null, null, null));

			// then
			assertThat(response.content()).hasSize(1);
			verify(salesReconcileLogRepository, never()).findAllByRepaired(anyBoolean(), any());
		}

		@Test
		@DisplayName("repaired 파라미터가 있으면 해당 값으로 필터링한다")
		void filtersByRepairedWhenProvided() {
			// given
			Page<SalesReconcileLog> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
			given(salesReconcileLogRepository.findAllByRepaired(eq(true), any())).willReturn(page);

			// when
			PageResponse<ReconcileLogResponse> response = service.getList(
					new ReconcileLogSearchRequest(null, null, true));

			// then
			assertThat(response.content()).isEmpty();
			verify(salesReconcileLogRepository, never()).findAll(any(Pageable.class));
		}
	}
}
