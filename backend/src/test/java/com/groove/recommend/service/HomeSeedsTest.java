package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HomeSeedsTest {

	@Nested
	@DisplayName("of()")
	class Of {

		@Test
		@DisplayName("위시와 구매가 겹치는 상품은 시드에 한 번만 담긴다")
		void mergesOverlappingWishedAndPurchasedIntoSeedIds() {
			// given
			List<Long> wished = List.of(1L, 2L);
			List<Long> purchased = List.of(2L, 3L);

			// when
			HomeSeeds homeSeeds = HomeSeeds.of(wished, purchased, List.of());

			// then
			assertThat(homeSeeds.seedIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
		}

		@Test
		@DisplayName("최근 본 상품 중 위시하거나 구매한 것은 recentOnlySeedIds 에서 뺀다")
		void keepsOnlyUnownedRecentIdsAsRecentOnly() {
			// given
			List<Long> wished = List.of(1L);
			List<Long> purchased = List.of(2L);
			List<Long> recent = List.of(1L, 2L, 3L, 4L);

			// when
			HomeSeeds homeSeeds = HomeSeeds.of(wished, purchased, recent);

			// then
			assertThat(homeSeeds.recentOnlySeedIds()).containsExactly(3L, 4L);
			assertThat(homeSeeds.seedIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
		}

		@Test
		@DisplayName("입력이 전부 비어 있으면 시드도 빈다")
		void returnsEmptySeedsWhenAllInputsAreEmpty() {
			// when
			HomeSeeds homeSeeds = HomeSeeds.of(List.of(), List.of(), List.of());

			// then
			assertThat(homeSeeds.seedIds()).isEmpty();
			assertThat(homeSeeds.recentOnlySeedIds()).isEmpty();
		}

		@Test
		@DisplayName("최근 본 상품 순서를 recentOnlySeedIds 에 보존한다")
		void preservesRecentIdOrderInRecentOnlySeedIds() {
			// given
			List<Long> recent = List.of(3L, 1L, 2L);

			// when
			HomeSeeds homeSeeds = HomeSeeds.of(Set.of(), Set.of(), recent);

			// then
			assertThat(homeSeeds.recentOnlySeedIds()).containsExactly(3L, 1L, 2L);
		}
	}
}
