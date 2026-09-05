package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductSortType;
import com.groove.admin.dto.PopularProductStatsCondition;
import com.groove.admin.dto.PopularProductStatsRequest;
import com.groove.admin.dto.StatsPeriodRequest;
import com.groove.admin.mapper.AdminStatsMapper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.entity.LimitedDropStatus;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

	@Mock
	AdminStatsMapper adminStatsMapper;

	AdminStatsService adminStatsService;

	LocalDate today;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-04T15:00:00Z"), ZoneId.of("Asia/Seoul"));
		today = LocalDate.now(clock);
		adminStatsService = new AdminStatsService(adminStatsMapper, clock);
	}

	@Nested
	@DisplayName("getDailySales()")
	class GetDailySales {

		@Test
		@DisplayName("from·to 를 모두 생략하면 최근 30일 창을 조회하고 빈 날은 0 으로 채운다")
		void resolvesDefaultThirtyDayWindowAndFillsGaps() {
			// given
			given(adminStatsMapper.findDailySales(any(), any())).willReturn(List.of());

			// when
			List<DailySalesResponse> result = adminStatsService.getDailySales(new StatsPeriodRequest(null, null));

			// then
			verify(adminStatsMapper).findDailySales(today.minusDays(29).atStartOfDay(),
					today.plusDays(1).atStartOfDay());
			assertThat(result).hasSize(30);
			assertThat(result).allSatisfy(row -> {
				assertThat(row.orderCount()).isZero();
				assertThat(row.salesAmount()).isEqualByComparingTo(BigDecimal.ZERO);
				assertThat(row.cancelAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			});
		}

		@Test
		@DisplayName("from 만 주어지면 to 는 오늘로 보정한다")
		void resolvesToAsTodayWhenOnlyFromGiven() {
			// given
			LocalDate from = today.minusDays(5);
			given(adminStatsMapper.findDailySales(any(), any())).willReturn(List.of());

			// when
			adminStatsService.getDailySales(new StatsPeriodRequest(from, null));

			// then
			verify(adminStatsMapper).findDailySales(from.atStartOfDay(), today.plusDays(1).atStartOfDay());
		}

		@Test
		@DisplayName("to 만 주어지면 from 은 to 기준 30일 창으로 보정한다")
		void resolvesFromAsThirtyDayWindowWhenOnlyToGiven() {
			// given
			LocalDate to = today.minusDays(1);
			given(adminStatsMapper.findDailySales(any(), any())).willReturn(List.of());

			// when
			adminStatsService.getDailySales(new StatsPeriodRequest(null, to));

			// then
			verify(adminStatsMapper).findDailySales(to.minusDays(29).atStartOfDay(), to.plusDays(1).atStartOfDay());
		}

		@Test
		@DisplayName("기존 매퍼 결과는 해당 날짜에 그대로 배치되고 빈 날은 0 으로 채워진다")
		void placesExistingRowsAndFillsGaps() {
			// given
			LocalDate from = today.minusDays(2);
			DailySalesResponse existing = new DailySalesResponse(from, 2, new BigDecimal("50000"),
					BigDecimal.ZERO);
			given(adminStatsMapper.findDailySales(any(), any())).willReturn(List.of(existing));

			// when
			List<DailySalesResponse> result = adminStatsService.getDailySales(new StatsPeriodRequest(from, today));

			// then
			assertThat(result).hasSize(3);
			assertThat(result.get(0)).isEqualTo(existing);
			assertThat(result.get(1).orderCount()).isZero();
			assertThat(result.get(2).orderCount()).isZero();
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 COMMON_INVALID_INPUT 예외를 던지고 매퍼를 호출하지 않는다")
		void throwsWhenFromAfterTo() {
			// given
			StatsPeriodRequest request = new StatsPeriodRequest(today, today.minusDays(1));

			// when & then
			assertThatThrownBy(() -> adminStatsService.getDailySales(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(adminStatsMapper, never()).findDailySales(any(), any());
		}

		@Test
		@DisplayName("기간이 365일 이상이면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenPeriodTooLong() {
			// given
			StatsPeriodRequest request = new StatsPeriodRequest(LocalDate.of(2025, 9, 5), today);

			// when & then
			assertThatThrownBy(() -> adminStatsService.getDailySales(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(adminStatsMapper, never()).findDailySales(any(), any());
		}

		@Test
		@DisplayName("기간이 364일이면 정상 조회된다")
		void allowsPeriodOfThreeHundredSixtyFourDays() {
			// given
			LocalDate from = today.minusDays(364);
			StatsPeriodRequest request = new StatsPeriodRequest(from, today);
			given(adminStatsMapper.findDailySales(any(), any())).willReturn(List.of());

			// when
			List<DailySalesResponse> result = adminStatsService.getDailySales(request);

			// then
			assertThat(result).hasSize(365);
			verify(adminStatsMapper).findDailySales(from.atStartOfDay(), today.plusDays(1).atStartOfDay());
		}
	}

	@Nested
	@DisplayName("getPopularProducts()")
	class GetPopularProducts {

		@Test
		@DisplayName("기본값은 limit 10·sort QUANTITY 이다")
		void usesDefaultLimitAndQuantitySort() {
			// given
			given(adminStatsMapper.findPopularProducts(any())).willReturn(List.of());
			ArgumentCaptor<PopularProductStatsCondition> captor =
					ArgumentCaptor.forClass(PopularProductStatsCondition.class);
			PopularProductStatsRequest request = new PopularProductStatsRequest(null, null, null, null);

			// when
			adminStatsService.getPopularProducts(request);

			// then
			verify(adminStatsMapper).findPopularProducts(captor.capture());
			assertThat(captor.getValue().limit()).isEqualTo(10);
			assertThat(captor.getValue().sort()).isEqualTo(PopularProductSortType.QUANTITY);
		}

		@Test
		@DisplayName("sort=sales 를 지정하면 SALES 로 조회한다")
		void resolvesSalesSort() {
			// given
			given(adminStatsMapper.findPopularProducts(any())).willReturn(List.of());
			ArgumentCaptor<PopularProductStatsCondition> captor =
					ArgumentCaptor.forClass(PopularProductStatsCondition.class);
			PopularProductStatsRequest request = new PopularProductStatsRequest(null, null, null, "sales");

			// when
			adminStatsService.getPopularProducts(request);

			// then
			verify(adminStatsMapper).findPopularProducts(captor.capture());
			assertThat(captor.getValue().sort()).isEqualTo(PopularProductSortType.SALES);
		}

		@ParameterizedTest
		@CsvSource({"0", "101"})
		@DisplayName("limit 이 1~100 범위를 벗어나면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenLimitOutOfRange(int limit) {
			// given
			PopularProductStatsRequest request = new PopularProductStatsRequest(null, null, limit, null);

			// when & then
			assertThatThrownBy(() -> adminStatsService.getPopularProducts(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(adminStatsMapper, never()).findPopularProducts(any());
		}

		@Test
		@DisplayName("허용되지 않은 sort 값이면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenSortInvalid() {
			// given
			PopularProductStatsRequest request = new PopularProductStatsRequest(null, null, null, "foo");

			// when & then
			assertThatThrownBy(() -> adminStatsService.getPopularProducts(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(adminStatsMapper, never()).findPopularProducts(any());
		}
	}

	@Nested
	@DisplayName("getSummary()")
	class GetSummary {

		@Test
		@DisplayName("오늘 00시부터 다음날 00시까지를 매퍼에 넘긴다")
		void callsMapperWithTodayAndTomorrowBoundaries() {
			// given
			AdminStatsSummaryResponse response = new AdminStatsSummaryResponse(BigDecimal.ZERO, 0, 0, 0);
			given(adminStatsMapper.findSummary(any(), any())).willReturn(response);

			// when
			AdminStatsSummaryResponse result = adminStatsService.getSummary();

			// then
			verify(adminStatsMapper).findSummary(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
			assertThat(result).isEqualTo(response);
		}
	}

	@Nested
	@DisplayName("getLimitedDropStats()")
	class GetLimitedDropStats {

		@Test
		@DisplayName("매퍼 결과를 그대로 반환한다")
		void passesThroughMapperResult() {
			// given
			LimitedDropStatsResponse response = new LimitedDropStatsResponse(1L, "그루브 앨범",
					LimitedDropStatus.OPEN, 10, 3, 30.0, LocalDateTime.now(),
					LocalDateTime.now().plusDays(1), null, null);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(response));

			// when
			List<LimitedDropStatsResponse> result = adminStatsService.getLimitedDropStats();

			// then
			assertThat(result).containsExactly(response);
		}
	}
}
