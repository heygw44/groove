package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.LimitedDropStatsRow;
import com.groove.admin.mapper.AdminStatsMapper;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropRedisService;

@ExtendWith(MockitoExtension.class)
class AdminLimitedDropStatsServiceTest {

	@Mock
	AdminStatsMapper adminStatsMapper;

	@Mock
	LimitedDropRedisService limitedDropRedisService;

	AdminLimitedDropStatsService adminLimitedDropStatsService;

	@BeforeEach
	void setUp() {
		adminLimitedDropStatsService = new AdminLimitedDropStatsService(adminStatsMapper, limitedDropRedisService);
	}

	private LimitedDropStatsRow rowOf(LimitedDropStatus status, int totalQuantity, int soldQuantity,
			Long soldOutCount, Long alreadyPurchasedCount, Long notOpenCount, Long closedCount) {
		return new LimitedDropStatsRow(1L, "그루브 앨범", status, totalQuantity, soldQuantity, 0.0,
				LocalDateTime.now(), LocalDateTime.now().plusDays(1), null, null,
				soldOutCount, alreadyPurchasedCount, notOpenCount, closedCount);
	}

	@Nested
	@DisplayName("getLimitedDropStats()")
	class GetLimitedDropStats {

		@Test
		@DisplayName("CLOSED 드롭은 DB 실패 집계를 쓰고 Redis 를 호출하지 않는다")
		void usesDbCountsForClosedDropWithoutCallingRedis() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 6, 2L, 1L, 0L, 1L);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result).hasSize(1);
			assertThat(result.get(0).attempts()).isNotNull();
			assertThat(result.get(0).attempts().successCount()).isEqualTo(6);
			assertThat(result.get(0).attempts().soldOutCount()).isEqualTo(2);
			assertThat(result.get(0).attempts().alreadyPurchasedCount()).isEqualTo(1);
			assertThat(result.get(0).attempts().notOpenCount()).isZero();
			assertThat(result.get(0).attempts().closedCount()).isEqualTo(1);
			assertThat(result.get(0).attempts().attemptCount()).isEqualTo(10);
			verify(limitedDropRedisService, never()).getAttempts(any());
		}

		@Test
		@DisplayName("진행 중인 드롭은 Redis 시도 집계를 읽는다")
		void readsRedisAttemptsForOpenDrop() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.OPEN, 10, 3, null, null, null, null);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));
			given(limitedDropRedisService.getAttempts(1L)).willReturn(Map.of(
					LimitedAttemptResult.SOLD_OUT, 4L,
					LimitedAttemptResult.NOT_OPEN, 2L));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result.get(0).attempts()).isNotNull();
			assertThat(result.get(0).attempts().successCount()).isEqualTo(3);
			assertThat(result.get(0).attempts().soldOutCount()).isEqualTo(4);
			assertThat(result.get(0).attempts().notOpenCount()).isEqualTo(2);
			assertThat(result.get(0).attempts().alreadyPurchasedCount()).isZero();
			assertThat(result.get(0).attempts().closedCount()).isZero();
			assertThat(result.get(0).attempts().attemptCount()).isEqualTo(9);
		}

		@Test
		@DisplayName("DB 행도 없고 Redis 집계도 비어 있으면 성공 수와 무관하게 attempts 가 null 이다")
		void returnsNullAttemptsWhenBothSourcesEmpty() {
			// given
			LimitedDropStatsRow closedRow = rowOf(LimitedDropStatus.CLOSED, 10, 6, null, null, null, null);
			LimitedDropStatsRow openRow = rowOf(LimitedDropStatus.OPEN, 10, 5, null, null, null, null);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(closedRow, openRow));
			given(limitedDropRedisService.getAttempts(1L)).willReturn(Map.of());

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result).extracting(LimitedDropStatsResponse::attempts).containsOnlyNulls();
		}

		@Test
		@DisplayName("경쟁률은 시도 수를 총 수량으로 나눠 소수 첫째 자리에서 반올림한다")
		void roundsCompetitionRateToOneDecimalPlace() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 3, 3, 4L, 0L, 0L, 0L);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			// attemptCount = 3(성공) + 4(soldOut) = 7, totalQuantity = 3 -> 7/3 = 2.33... -> 2.3
			assertThat(result.get(0).attempts().competitionRate()).isEqualTo(2.3);
		}

		@Test
		@DisplayName("총 수량이 0 이면 경쟁률은 0 이다")
		void returnsZeroCompetitionRateWhenTotalQuantityZero() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 0, 0, 1L, 0L, 0L, 0L);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result.get(0).attempts().competitionRate()).isZero();
		}

		@Test
		@DisplayName("soldOutCount 만 없고 나머지 실패 집계가 있으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyAlreadyPurchasedCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, 3L, null, null);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result.get(0).attempts()).isNotNull();
			assertThat(result.get(0).attempts().soldOutCount()).isZero();
			assertThat(result.get(0).attempts().alreadyPurchasedCount()).isEqualTo(3);
			assertThat(result.get(0).attempts().attemptCount()).isEqualTo(8);
		}

		@Test
		@DisplayName("soldOutCount 와 alreadyPurchasedCount 만 없고 notOpenCount 가 있으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyNotOpenCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, null, 2L, null);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result.get(0).attempts()).isNotNull();
			assertThat(result.get(0).attempts().notOpenCount()).isEqualTo(2);
			assertThat(result.get(0).attempts().attemptCount()).isEqualTo(7);
		}

		@Test
		@DisplayName("closedCount 만 있고 나머지 실패 집계가 없으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyClosedCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, null, null, 1L);
			given(adminStatsMapper.findLimitedDropStats()).willReturn(List.of(row));

			// when
			List<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats();

			// then
			assertThat(result.get(0).attempts()).isNotNull();
			assertThat(result.get(0).attempts().closedCount()).isEqualTo(1);
			assertThat(result.get(0).attempts().attemptCount()).isEqualTo(6);
		}
	}
}
