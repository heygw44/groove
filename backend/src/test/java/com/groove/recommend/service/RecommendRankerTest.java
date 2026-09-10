package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.service.RecommendRanker.RankedCandidate;

class RecommendRankerTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 10, 0);

	// bayes tie-break 이 averageRating 순서를 그대로 따르도록 리뷰 수를 후보끼리 동일하게 맞춘다.
	private static final int REVIEW_COUNT = 10;

	private final RecommendScorer recommendScorer = new RecommendScorer(RecommendWeights.DEFAULT);
	private final RecommendRanker recommendRanker = new RecommendRanker(recommendScorer, RecommendWeights.DEFAULT);

	// labelId·decade 는 null, genreIds 는 빈 집합으로 둬 SAME_ARTIST 외 다른 차원이 우연히 매칭되지 않게 한다.
	private ProductFeature feature(Long id, Long albumId, Long artistId, Double averageRating) {
		return new ProductFeature(id, albumId, artistId, null, Set.of(), null, averageRating, NOW, REVIEW_COUNT, 0L,
				ProductStatus.ON_SALE);
	}

	private ProductFeature seed(Long id, Long artistId) {
		return new ProductFeature(id, id, artistId, null, Set.of(), null, 4.0, NOW, REVIEW_COUNT, 0L,
				ProductStatus.ON_SALE);
	}

	private Map<Long, ProductFeature> featuresOf(ProductFeature... features) {
		Map<Long, ProductFeature> result = new LinkedHashMap<>();
		for (ProductFeature feature : features) {
			result.put(feature.id(), feature);
		}
		return result;
	}

	@Nested
	@DisplayName("rank()")
	class Rank {

		@Test
		@DisplayName("총점이 0 이하인 후보는 결과에서 뺀다")
		void filtersOutNonPositiveScore() {
			// given
			ProductFeature unrelated = feature(1L, 1L, 99L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(unrelated);
			List<ProductFeature> seeds = List.of(seed(10L, 1L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("exclude 로 지정한 상품은 시드와 매칭되어도 결과에서 뺀다")
		void excludesCandidatesInExcludeIds() {
			// given
			ProductFeature matching = feature(1L, 1L, 5L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(matching);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(1L), 10);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("입력 후보가 없으면 빈 결과를 반환한다")
		void returnsEmptyWhenNoFeatures() {
			// when
			List<RankedCandidate> ranked = recommendRanker.rank(Map.of(), TasteSignal.empty(), List.of(), Set.of(),
					Map.of(), Set.of(), 10);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("같은 앨범의 후보는 점수가 가장 높은 것만 남긴다")
		void dedupsCandidatesByAlbumKeepingHighestScore() {
			// given — albumId 1 아래 두 프레싱. artistId 로 SAME_ARTIST(4점) 만 맞춘 쪽이 bayes tie-break 로도 이긴다
			ProductFeature higherRated = feature(1L, 1L, 5L, 5.0);
			ProductFeature lowerRated = feature(2L, 1L, 5L, 3.0);
			Map<Long, ProductFeature> features = featuresOf(higherRated, lowerRated);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L);
		}

		@Test
		@DisplayName("앨범 dedup 은 size 로 자르기 전에 적용해 결과가 size 만큼 채워진다")
		void appliesAlbumDedupBeforeSizeCut() {
			// given — 앨범 1 에 두 프레싱, 앨범 2 에 한 개. dedup 을 size(2) 컷 뒤에 하면 결과가 1건으로 줄어든다
			ProductFeature albumOnePressingA = feature(1L, 1L, 5L, 5.0);
			ProductFeature albumOnePressingB = feature(2L, 1L, 5L, 4.0);
			ProductFeature albumTwo = feature(3L, 2L, 5L, 3.0);
			Map<Long, ProductFeature> features = featuresOf(albumOnePressingA, albumOnePressingB, albumTwo);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 2);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 3L);
		}

		@Test
		@DisplayName("hidden 상품은 후보에서 뺀다")
		void excludesHiddenFeatures() {
			// given
			ProductFeature hidden = new ProductFeature(1L, 1L, 5L, 1L, Set.of(), Decade.D1990, 4.0, NOW, REVIEW_COUNT,
					0L, ProductStatus.HIDDEN);
			Map<Long, ProductFeature> features = featuresOf(hidden);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("지정한 가중치로 랭킹하면 기본 가중치와 다른 점수로 정렬한다")
		void ranksWithGivenWeightsOverload() {
			// given — SAME_ARTIST 를 0 으로 죽인 가중치로는 매칭이 있어도 총점이 0 이라 결과가 빈다
			ProductFeature matching = feature(1L, 1L, 5L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(matching);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));
			RecommendWeights zeroedSameArtist = RecommendWeights.DEFAULT.with(RecommendReason.SAME_ARTIST, 0);

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10, zeroedSameArtist);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("가중치를 지정하지 않으면 주입된 기본 가중치로 랭킹한다")
		void delegatesToDefaultOverloadWithInjectedWeights() {
			// given
			ProductFeature matching = feature(1L, 1L, 5L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(matching);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> defaultRanked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10);
			List<RankedCandidate> explicitRanked = recommendRanker.rank(features, TasteSignal.empty(), seeds,
					Set.of(), Map.of(), Set.of(), 10, RecommendWeights.DEFAULT);

			// then
			assertThat(defaultRanked.get(0).score().totalScore())
					.isEqualTo(explicitRanked.get(0).score().totalScore());
		}
	}
}
