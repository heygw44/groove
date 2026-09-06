package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.recommend.dto.CoPurchaseRow;
import com.groove.recommend.mapper.RecommendQueryMapper;

@ExtendWith(MockitoExtension.class)
class BoughtTogetherAggregatorTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private RecommendQueryMapper recommendQueryMapper;

	@Mock
	private BoughtTogetherRedisService boughtTogetherRedisService;

	private Clock clock;

	private BoughtTogetherAggregator boughtTogetherAggregator;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-06T03:00:00Z"), ZONE);
		boughtTogetherAggregator = new BoughtTogetherAggregator(recommendQueryMapper, boughtTogetherRedisService,
				clock);
	}

	@Nested
	@DisplayName("refresh()")
	class Refresh {

		@Test
		@DisplayName("365일 전 시각을 기준으로 조회한다")
		void queriesWithThreeHundredSixtyFiveDayThreshold() {
			// given
			LocalDateTime expectedSinceAt = LocalDateTime.now(clock)
					.minusDays(BoughtTogetherAggregator.WINDOW_DAYS);
			given(recommendQueryMapper.countCoPurchases(any())).willReturn(List.of());
			ArgumentCaptor<LocalDateTime> sinceAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

			// when
			boughtTogetherAggregator.refresh();

			// then
			verify(recommendQueryMapper).countCoPurchases(sinceAtCaptor.capture());
			assertThat(sinceAtCaptor.getValue()).isEqualTo(expectedSinceAt);
		}

		@Test
		@DisplayName("조회 결과를 상품별로 묶어 Redis 적재를 요청한다")
		void groupsCountsByProductAndReplacesRedis() {
			// given
			given(recommendQueryMapper.countCoPurchases(any())).willReturn(List.of(
					new CoPurchaseRow(1L, 2L, 5L),
					new CoPurchaseRow(1L, 3L, 2L),
					new CoPurchaseRow(2L, 1L, 5L)));
			@SuppressWarnings("unchecked")
			ArgumentCaptor<Map<Long, Map<Long, Long>>> countsCaptor = ArgumentCaptor.forClass(Map.class);

			// when
			int result = boughtTogetherAggregator.refresh();

			// then
			verify(boughtTogetherRedisService).replaceAll(countsCaptor.capture());
			Map<Long, Map<Long, Long>> counts = countsCaptor.getValue();
			assertThat(counts.get(1L)).containsEntry(2L, 5L).containsEntry(3L, 2L);
			assertThat(counts.get(2L)).containsEntry(1L, 5L);
			assertThat(result).isEqualTo(2);
		}

		@Test
		@DisplayName("조회 결과가 없으면 Redis 적재를 요청하지 않고 0을 반환한다")
		void skipsRedisReplaceWhenNoRows() {
			// given
			given(recommendQueryMapper.countCoPurchases(any())).willReturn(List.of());

			// when
			int result = boughtTogetherAggregator.refresh();

			// then
			verify(boughtTogetherRedisService, never()).replaceAll(any());
			assertThat(result).isEqualTo(0);
		}
	}
}
