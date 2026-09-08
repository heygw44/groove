package com.groove.stats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.stats.dto.SalesAggregationRequest;
import com.groove.stats.dto.SalesAggregationResponse;

@ExtendWith(MockitoExtension.class)
class SalesAggregationAdminServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Long ADMIN_ID = 1L;

	@Mock
	private SalesAggregationService salesAggregationService;

	@Mock
	private AggregationLock aggregationLock;

	@Mock
	private AdminAuditLogService adminAuditLogService;

	private SalesAggregationAdminService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2031-03-16T00:00:00Z"), ZONE);
		service = new SalesAggregationAdminService(salesAggregationService, aggregationLock, adminAuditLogService,
				clock);
	}

	private void stubLockRunsTask() {
		given(aggregationLock.runExclusively(any())).willAnswer(invocation -> {
			Runnable task = invocation.getArgument(0);
			task.run();
			return true;
		});
	}

	@Nested
	@DisplayName("aggregate()")
	class Aggregate {

		@Test
		@DisplayName("기간 내 날짜 수만큼 재집계하고 감사 로그를 남긴다")
		void aggregatesEachDateAndRecordsAudit() {
			// given
			stubLockRunsTask();
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2031, 3, 1),
					LocalDate.of(2031, 3, 5));

			// when
			SalesAggregationResponse response = service.aggregate(ADMIN_ID, request);

			// then
			assertThat(response.aggregatedDays()).isEqualTo(5);
			verify(salesAggregationService, times(5)).aggregateDate(any());
			verify(adminAuditLogService).record(eq(ADMIN_ID), eq(AdminAuditAction.SALES_AGGREGATION_RUN),
					eq(AdminAuditTargetType.SALES_AGGREGATION), isNull(), any());
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenFromAfterTo() {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2031, 3, 10),
					LocalDate.of(2031, 3, 1));

			// when & then
			assertThatThrownBy(() -> service.aggregate(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(salesAggregationService, never()).aggregateDate(any());
		}

		@Test
		@DisplayName("락 획득에 실패하면 STATS_AGGREGATION_RUNNING 예외를 던진다")
		void throwsWhenLockNotAcquired() {
			// given
			given(aggregationLock.runExclusively(any())).willReturn(false);
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2031, 3, 1),
					LocalDate.of(2031, 3, 5));

			// when & then
			assertThatThrownBy(() -> service.aggregate(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STATS_AGGREGATION_RUNNING);
			verify(adminAuditLogService, never()).record(any(), any(), any(), any(), any());
		}
	}
}
