package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.mapper.RecommendQueryMapper;

@ExtendWith(MockitoExtension.class)
class RecentViewServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	private RecentViewRedisService recentViewRedisService;

	@Mock
	private RecommendQueryMapper recommendQueryMapper;

	private RecentViewService recentViewService;

	@BeforeEach
	void setUp() {
		recentViewService = new RecentViewService(recentViewRedisService, recommendQueryMapper);
	}

	private ProductSummaryResponse summary(Long id) {
		return new ProductSummaryResponse(id, "title-" + id, "artist", "label", BigDecimal.TEN, "Black", "180g",
				ProductStatus.ON_SALE, "thumb", 4.5, 3L, null);
	}

	@Nested
	@DisplayName("getRecentViews()")
	class GetRecentViews {

		@Test
		@DisplayName("Redis 목록 순서대로 응답을 정렬한다")
		void ordersByRecentViewSequence() {
			// given
			given(recentViewRedisService.findRecentProductIds(MEMBER_ID)).willReturn(List.of(3L, 1L, 2L));
			given(recommendQueryMapper.findSummariesByIds(List.of(3L, 1L, 2L), MEMBER_ID))
					.willReturn(List.of(summary(1L), summary(2L), summary(3L)));

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(MEMBER_ID);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(3L, 1L, 2L);
		}

		@Test
		@DisplayName("Redis 가 비면 DB 폴백 쿼리를 호출한다")
		void fallsBackToDatabaseWhenRedisEmpty() {
			// given
			given(recentViewRedisService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findRecentProductIds(MEMBER_ID, RecentViewRedisService.MAX_SIZE))
					.willReturn(List.of(5L));
			given(recommendQueryMapper.findSummariesByIds(List.of(5L), MEMBER_ID)).willReturn(List.of(summary(5L)));

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(MEMBER_ID);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(5L);
			verify(recommendQueryMapper).findRecentProductIds(MEMBER_ID, RecentViewRedisService.MAX_SIZE);
			verify(recentViewRedisService, never()).remove(eq(MEMBER_ID), anyList());
		}

		@Test
		@DisplayName("Redis 와 DB 둘 다 비면 요약 매퍼를 호출하지 않고 빈 목록을 반환한다")
		void returnsEmptyWhenBothSourcesEmpty() {
			// given
			given(recentViewRedisService.findRecentProductIds(MEMBER_ID)).willReturn(List.of());
			given(recommendQueryMapper.findRecentProductIds(MEMBER_ID, RecentViewRedisService.MAX_SIZE))
					.willReturn(List.of());

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(MEMBER_ID);

			// then
			assertThat(result).isEmpty();
			verify(recommendQueryMapper, never()).findSummariesByIds(anyList(), eq(MEMBER_ID));
		}

		@Test
		@DisplayName("매퍼 결과에서 빠진 상품은 응답에서 제외하고 Redis 에서도 제거한다")
		void excludesMissingSummariesAndCleansUpRedis() {
			// given
			given(recentViewRedisService.findRecentProductIds(MEMBER_ID)).willReturn(List.of(1L, 2L, 3L));
			given(recommendQueryMapper.findSummariesByIds(List.of(1L, 2L, 3L), MEMBER_ID))
					.willReturn(List.of(summary(1L), summary(3L)));

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(MEMBER_ID);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(1L, 3L);
			verify(recentViewRedisService).remove(MEMBER_ID, List.of(2L));
		}

		@Test
		@DisplayName("중복된 id 는 한 번만 조회·반환한다")
		void deduplicatesIds() {
			// given
			given(recentViewRedisService.findRecentProductIds(MEMBER_ID)).willReturn(List.of(1L, 2L, 1L));
			given(recommendQueryMapper.findSummariesByIds(List.of(1L, 2L), MEMBER_ID))
					.willReturn(List.of(summary(1L), summary(2L)));

			// when
			List<ProductSummaryResponse> result = recentViewService.getRecentViews(MEMBER_ID);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(1L, 2L);
			verify(recommendQueryMapper).findSummariesByIds(List.of(1L, 2L), MEMBER_ID);
		}
	}
}
