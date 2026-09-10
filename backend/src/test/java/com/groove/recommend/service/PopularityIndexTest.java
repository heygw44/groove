package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.product.entity.ProductStatus;

class PopularityIndexTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 10, 0);
	private static final double TOLERANCE = 1e-6;

	private ProductFeature feature(Long id, Double averageRating, int reviewCount, long soldQuantity,
			ProductStatus status) {
		return feature(id, id, averageRating, reviewCount, soldQuantity, status, NOW);
	}

	private ProductFeature feature(Long id, Long albumId, Double averageRating, int reviewCount, long soldQuantity,
			ProductStatus status, LocalDateTime createdAt) {
		return new ProductFeature(id, albumId, 1L, null, Set.of(), null, averageRating, createdAt, reviewCount,
				soldQuantity, status);
	}

	@Nested
	@DisplayName("bayes()")
	class Bayes {

		@Test
		@DisplayName("리뷰가 있으면 (v·R + m·C) / (v + m) 으로 스무딩한다")
		void smoothsRatingWithPriorWhenReviewsExist() {
			// given — C = (2.0 + 4.5 + 4.5) / 3
			List<ProductFeature> features = List.of(
					feature(1L, 2.0, 1, 0, ProductStatus.ON_SALE),
					feature(2L, 4.5, 8, 0, ProductStatus.ON_SALE),
					feature(3L, 4.5, 8, 0, ProductStatus.ON_SALE));
			PopularityIndex index = PopularityIndex.from(features);

			// when & then
			assertThat(index.bayes(features.get(0))).isCloseTo(3.388889, within(TOLERANCE));
			assertThat(index.bayes(features.get(1))).isCloseTo(4.179487, within(TOLERANCE));
		}

		@Test
		@DisplayName("리뷰가 0건이면 그대로 C 가 된다")
		void returnsGlobalAverageWhenNoReviews() {
			// given
			List<ProductFeature> features = List.of(
					feature(1L, null, 0, 0, ProductStatus.ON_SALE),
					feature(2L, 5.0, 10, 0, ProductStatus.ON_SALE));
			PopularityIndex index = PopularityIndex.from(features);

			// when
			double bayes = index.bayes(features.get(0));

			// then
			assertThat(bayes).isCloseTo(5.0, within(TOLERANCE));
		}
	}

	@Nested
	@DisplayName("popularity()")
	class Popularity {

		@Test
		@DisplayName("판매량이 전무하면 bayes 항만으로 수렴한다")
		void convergesToBayesTermWhenNoSales() {
			// given
			List<ProductFeature> features = List.of(
					feature(1L, 4.0, 10, 0, ProductStatus.ON_SALE),
					feature(2L, 4.0, 10, 0, ProductStatus.ON_SALE));
			PopularityIndex index = PopularityIndex.from(features);

			// when
			double popularity = index.popularity(features.get(0));

			// then — bayes = C = 4.0 이므로 popularity = 0.4 · (4.0/5.0)
			assertThat(popularity).isCloseTo(0.32, within(TOLERANCE));
		}

		@Test
		@DisplayName("판매량이 많을수록 로그 스케일로 가산된다")
		void addsLogScaledSalesTerm() {
			// given — 두 후보의 bayes 는 같고 판매량만 다르다
			List<ProductFeature> features = List.of(
					feature(1L, 4.0, 10, 0, ProductStatus.ON_SALE),
					feature(2L, 4.0, 10, 100, ProductStatus.ON_SALE));
			PopularityIndex index = PopularityIndex.from(features);

			// when
			double noSales = index.popularity(features.get(0));
			double withSales = index.popularity(features.get(1));

			// then
			assertThat(withSales).isGreaterThan(noSales);
		}
	}

	@Nested
	@DisplayName("topOnSaleProductIds()")
	class TopOnSaleProductIds {

		@Test
		@DisplayName("ON_SALE 이 아닌 상품은 뺀다")
		void excludesNonOnSaleProducts() {
			// given
			List<ProductFeature> features = List.of(
					feature(1L, 5.0, 10, 0, ProductStatus.ON_SALE),
					feature(2L, 5.0, 10, 0, ProductStatus.HIDDEN),
					feature(3L, 5.0, 10, 0, ProductStatus.SOLD_OUT));
			PopularityIndex index = PopularityIndex.from(features);

			// when
			List<Long> result = index.topOnSaleProductIds(features, 10);

			// then
			assertThat(result).containsExactly(1L);
		}

		@Test
		@DisplayName("popularity 내림차순 → 최신순 → id 내림차순으로 상위 limit 개를 뽑는다")
		void ordersByPopularityThenCreatedAtThenId() {
			// given — 1,2 는 popularity 가 같아(rating·리뷰 동일) createdAt 이 가른다. 3 은 판매량이 많아 1위다
			List<ProductFeature> features = List.of(
					feature(1L, 1L, 4.0, 10, 0, ProductStatus.ON_SALE, NOW.minusDays(1)),
					feature(2L, 2L, 4.0, 10, 0, ProductStatus.ON_SALE, NOW),
					feature(3L, 3L, 4.0, 10, 1000, ProductStatus.ON_SALE, NOW.minusDays(2)));
			PopularityIndex index = PopularityIndex.from(features);

			// when
			List<Long> result = index.topOnSaleProductIds(features, 2);

			// then
			assertThat(result).containsExactly(3L, 2L);
		}
	}
}
