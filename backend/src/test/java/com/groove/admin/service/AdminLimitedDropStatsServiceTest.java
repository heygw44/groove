package com.groove.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.groove.admin.dto.LimitedDropStatsRequest;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.LimitedDropStatsRow;
import com.groove.admin.mapper.AdminStatsMapper;
import com.groove.global.common.PageResponse;
import com.groove.limited.entity.LimitedAttemptResult;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropRedisService;

@ExtendWith(MockitoExtension.class)
class AdminLimitedDropStatsServiceTest {

	private static final LimitedDropStatsRequest DEFAULT_REQUEST = new LimitedDropStatsRequest(null, null);

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

	private void givenRows(LimitedDropStatsRow... rows) {
		given(adminStatsMapper.countLimitedDropStats()).willReturn((long) rows.length);
		given(adminStatsMapper.findLimitedDropStats(0, 20)).willReturn(List.of(rows));
	}

	@Nested
	@DisplayName("getLimitedDropStats()")
	class GetLimitedDropStats {

		@Test
		@DisplayName("전체 건수가 0 이면 Redis 를 호출하지 않고 빈 페이지를 반환한다")
		void returnsEmptyPageWithoutCallingRedisWhenNoRows() {
			// given
			given(adminStatsMapper.countLimitedDropStats()).willReturn(0L);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			assertThat(result.content()).isEmpty();
			assertThat(result.totalElements()).isZero();
			verify(adminStatsMapper, never()).findLimitedDropStats(any(int.class), any(int.class));
			verify(limitedDropRedisService, never()).getAttempts(any(java.util.Collection.class));
		}

		@Test
		@DisplayName("CLOSED 드롭은 DB 실패 집계를 쓰고 Redis 조회 대상에서 뺀다")
		void usesDbCountsForClosedDropWithoutCallingRedis() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 6, 2L, 1L, 0L, 1L);
			givenRows(row);
			given(limitedDropRedisService.getAttempts(List.<Long>of())).willReturn(Map.of());

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			LimitedDropStatsResponse response = result.content().get(0);
			assertThat(response.attempts()).isNotNull();
			assertThat(response.attempts().successCount()).isEqualTo(6);
			assertThat(response.attempts().soldOutCount()).isEqualTo(2);
			assertThat(response.attempts().alreadyPurchasedCount()).isEqualTo(1);
			assertThat(response.attempts().notOpenCount()).isZero();
			assertThat(response.attempts().closedCount()).isEqualTo(1);
			assertThat(response.attempts().attemptCount()).isEqualTo(10);
			verify(limitedDropRedisService).getAttempts(List.<Long>of());
		}

		@Test
		@DisplayName("진행 중인 드롭은 Redis 일괄 조회 결과에서 시도 집계를 읽는다")
		void readsBatchedRedisAttemptsForOpenDrop() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.OPEN, 10, 3, null, null, null, null);
			givenRows(row);
			given(limitedDropRedisService.getAttempts(List.of(1L))).willReturn(Map.of(1L, Map.of(
					LimitedAttemptResult.SOLD_OUT, 4L,
					LimitedAttemptResult.NOT_OPEN, 2L)));

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			LimitedDropStatsResponse response = result.content().get(0);
			assertThat(response.attempts()).isNotNull();
			assertThat(response.attempts().successCount()).isEqualTo(3);
			assertThat(response.attempts().soldOutCount()).isEqualTo(4);
			assertThat(response.attempts().notOpenCount()).isEqualTo(2);
			assertThat(response.attempts().alreadyPurchasedCount()).isZero();
			assertThat(response.attempts().closedCount()).isZero();
			assertThat(response.attempts().attemptCount()).isEqualTo(9);
			verify(limitedDropRedisService, times(1)).getAttempts(any(java.util.Collection.class));
		}

		@Test
		@DisplayName("여러 드롭이 있어도 Redis 일괄 조회는 한 번만 호출한다")
		void callsRedisBatchOnlyOnce() {
			// given
			LimitedDropStatsRow open1 = rowOf(LimitedDropStatus.OPEN, 10, 3, null, null, null, null);
			LimitedDropStatsRow open2 = rowOf(LimitedDropStatus.SOLD_OUT, 10, 10, null, null, null, null);
			given(adminStatsMapper.countLimitedDropStats()).willReturn(2L);
			given(adminStatsMapper.findLimitedDropStats(0, 20)).willReturn(List.of(open1, open2));
			given(limitedDropRedisService.getAttempts(any(java.util.Collection.class))).willReturn(Map.of());

			// when
			adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			verify(limitedDropRedisService, times(1)).getAttempts(any(java.util.Collection.class));
		}

		@Test
		@DisplayName("DB 행도 없고 Redis 집계도 비어 있으면 성공 수와 무관하게 attempts 가 null 이다")
		void returnsNullAttemptsWhenBothSourcesEmpty() {
			// given
			LimitedDropStatsRow closedRow = rowOf(LimitedDropStatus.CLOSED, 10, 6, null, null, null, null);
			LimitedDropStatsRow openRow = rowOf(LimitedDropStatus.OPEN, 10, 5, null, null, null, null);
			given(adminStatsMapper.countLimitedDropStats()).willReturn(2L);
			given(adminStatsMapper.findLimitedDropStats(0, 20)).willReturn(List.of(closedRow, openRow));
			given(limitedDropRedisService.getAttempts(any(java.util.Collection.class))).willReturn(Map.of());

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			assertThat(result.content()).extracting(LimitedDropStatsResponse::attempts).containsOnlyNulls();
		}

		@Test
		@DisplayName("경쟁률은 시도 수를 총 수량으로 나눠 소수 첫째 자리에서 반올림한다")
		void roundsCompetitionRateToOneDecimalPlace() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 3, 3, 4L, 0L, 0L, 0L);
			givenRows(row);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			// attemptCount = 3(성공) + 4(soldOut) = 7, totalQuantity = 3 -> 7/3 = 2.33... -> 2.3
			assertThat(result.content().get(0).attempts().competitionRate()).isEqualTo(2.3);
		}

		@Test
		@DisplayName("총 수량이 0 이면 경쟁률은 0 이다")
		void returnsZeroCompetitionRateWhenTotalQuantityZero() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 0, 0, 1L, 0L, 0L, 0L);
			givenRows(row);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			assertThat(result.content().get(0).attempts().competitionRate()).isZero();
		}

		@Test
		@DisplayName("soldOutCount 만 없고 나머지 실패 집계가 있으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyAlreadyPurchasedCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, 3L, null, null);
			givenRows(row);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			LimitedDropStatsResponse response = result.content().get(0);
			assertThat(response.attempts()).isNotNull();
			assertThat(response.attempts().soldOutCount()).isZero();
			assertThat(response.attempts().alreadyPurchasedCount()).isEqualTo(3);
			assertThat(response.attempts().attemptCount()).isEqualTo(8);
		}

		@Test
		@DisplayName("soldOutCount 와 alreadyPurchasedCount 만 없고 notOpenCount 가 있으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyNotOpenCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, null, 2L, null);
			givenRows(row);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			LimitedDropStatsResponse response = result.content().get(0);
			assertThat(response.attempts()).isNotNull();
			assertThat(response.attempts().notOpenCount()).isEqualTo(2);
			assertThat(response.attempts().attemptCount()).isEqualTo(7);
		}

		@Test
		@DisplayName("closedCount 만 있고 나머지 실패 집계가 없으면 attempts 는 null 이 아니다")
		void treatsAttemptsAsNotNullWhenOnlyClosedCountPresent() {
			// given
			LimitedDropStatsRow row = rowOf(LimitedDropStatus.CLOSED, 10, 5, null, null, null, 1L);
			givenRows(row);

			// when
			PageResponse<LimitedDropStatsResponse> result =
					adminLimitedDropStatsService.getLimitedDropStats(DEFAULT_REQUEST);

			// then
			LimitedDropStatsResponse response = result.content().get(0);
			assertThat(response.attempts()).isNotNull();
			assertThat(response.attempts().closedCount()).isEqualTo(1);
			assertThat(response.attempts().attemptCount()).isEqualTo(6);
		}

		@Test
		@DisplayName("페이지 조건에 맞춰 offset/size 를 매퍼에 전달한다")
		void passesOffsetAndSizeToMapper() {
			// given
			LimitedDropStatsRequest request = new LimitedDropStatsRequest(2, 10);
			given(adminStatsMapper.countLimitedDropStats()).willReturn(25L);
			given(adminStatsMapper.findLimitedDropStats(20, 10)).willReturn(List.of());

			// when
			PageResponse<LimitedDropStatsResponse> result = adminLimitedDropStatsService.getLimitedDropStats(request);

			// then
			assertThat(result.page()).isEqualTo(2);
			assertThat(result.size()).isEqualTo(10);
			assertThat(result.totalElements()).isEqualTo(25);
			verify(adminStatsMapper).findLimitedDropStats(20, 10);
		}
	}
}
