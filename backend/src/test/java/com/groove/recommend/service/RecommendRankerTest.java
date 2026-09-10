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
import com.groove.recommend.service.RecommendRanker.CapPolicy;
import com.groove.recommend.service.RecommendRanker.RankedCandidate;

class RecommendRankerTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 10, 0);

	// bayes tie-break 이 averageRating 순서를 그대로 따르도록 리뷰 수를 후보끼리 동일하게 맞춘다.
	private static final int REVIEW_COUNT = 10;

	private final RecommendScorer recommendScorer = new RecommendScorer(RecommendWeights.DEFAULT);
	private final RecommendRanker recommendRanker = new RecommendRanker(recommendScorer, RecommendWeights.DEFAULT);

	// labelId·decade 는 null, genreIds 는 빈 집합으로 둬 SAME_ARTIST 외 다른 차원이 우연히 매칭되지 않게 한다.
	private ProductFeature feature(Long id, Long albumId, Long artistId, Double averageRating) {
		return feature(id, albumId, artistId, null, averageRating);
	}

	private ProductFeature feature(Long id, Long albumId, Long artistId, Long labelId, Double averageRating) {
		return new ProductFeature(id, albumId, artistId, labelId, Set.of(), null, averageRating, NOW, REVIEW_COUNT,
				0L, ProductStatus.ON_SALE);
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
					Map.of(), Set.of(), 10, CapPolicy.HOME);

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
					Map.of(), Set.of(1L), 10, CapPolicy.HOME);

			// then
			assertThat(ranked).isEmpty();
		}

		@Test
		@DisplayName("입력 후보가 없으면 빈 결과를 반환한다")
		void returnsEmptyWhenNoFeatures() {
			// when
			List<RankedCandidate> ranked = recommendRanker.rank(Map.of(), TasteSignal.empty(), List.of(), Set.of(),
					Map.of(), Set.of(), 10, CapPolicy.HOME);

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
					Map.of(), Set.of(), 10, CapPolicy.HOME);

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
					Map.of(), Set.of(), 2, CapPolicy.HOME);

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
					Map.of(), Set.of(), 10, CapPolicy.HOME);

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
					Map.of(), Set.of(), 10, zeroedSameArtist, CapPolicy.HOME);

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
					Map.of(), Set.of(), 10, CapPolicy.HOME);
			List<RankedCandidate> explicitRanked = recommendRanker.rank(features, TasteSignal.empty(), seeds,
					Set.of(), Map.of(), Set.of(), 10, RecommendWeights.DEFAULT, CapPolicy.HOME);

			// then
			assertThat(defaultRanked.get(0).score().totalScore())
					.isEqualTo(explicitRanked.get(0).score().totalScore());
		}
	}

	@Nested
	@DisplayName("rank() — 아티스트/레이블 캡")
	class RankWithCap {

		@Test
		@DisplayName("아티스트 캡에 걸리면 건너뛰고 다음 순위 후보로 채운다")
		void skipsCandidateOverArtistCapAndFillsWithNextRanked() {
			// given — 아티스트 5 인 후보 3개(점수 내림차순 A>B>C) + 다른 아티스트 후보 D. maxPerArtist=2 면 C 대신 D 가 뽑힌다
			ProductFeature candidateA = feature(1L, 1L, 5L, 5.0);
			ProductFeature candidateB = feature(2L, 2L, 5L, 4.0);
			ProductFeature candidateC = feature(3L, 3L, 5L, 3.0);
			ProductFeature candidateD = feature(4L, 4L, 6L, 2.0);
			Map<Long, ProductFeature> features = featuresOf(candidateA, candidateB, candidateC, candidateD);
			List<ProductFeature> seeds = List.of(seed(10L, 5L), seed(11L, 6L));
			CapPolicy artistCapOnly = new CapPolicy(2, Integer.MAX_VALUE);

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 3, artistCapOnly);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 2L, 4L);
		}

		@Test
		@DisplayName("레이블 캡에 걸리면 건너뛰고 다음 순위 후보로 채운다")
		void skipsCandidateOverLabelCapAndFillsWithNextRanked() {
			// given — 레이블 100 인 후보 3개(아티스트는 서로 달라 아티스트 캡엔 안 걸림) + 다른 레이블 후보 D
			ProductFeature candidateA = feature(1L, 1L, 5L, 100L, 5.0);
			ProductFeature candidateB = feature(2L, 2L, 6L, 100L, 4.0);
			ProductFeature candidateC = feature(3L, 3L, 7L, 100L, 3.0);
			ProductFeature candidateD = feature(4L, 4L, 8L, 200L, 2.0);
			Map<Long, ProductFeature> features = featuresOf(candidateA, candidateB, candidateC, candidateD);
			List<ProductFeature> seeds = List.of(seed(10L, 5L), seed(11L, 6L), seed(12L, 7L), seed(13L, 8L));
			CapPolicy labelCapOnly = new CapPolicy(Integer.MAX_VALUE, 2);

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 3, labelCapOnly);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 2L, 4L);
		}

		@Test
		@DisplayName("labelId 가 null 인 후보끼리는 같은 캡 그룹으로 묶이지 않는다")
		void doesNotGroupNullLabelCandidatesTogether() {
			// given — 둘 다 labelId=null. maxPerLabel=1 이라도 null 끼리는 서로 다른 그룹이라 둘 다 살아남는다
			ProductFeature nullLabelFirst = feature(1L, 1L, 5L, null, 5.0);
			ProductFeature nullLabelSecond = feature(2L, 2L, 6L, null, 4.0);
			Map<Long, ProductFeature> features = featuresOf(nullLabelFirst, nullLabelSecond);
			List<ProductFeature> seeds = List.of(seed(10L, 5L), seed(11L, 6L));
			CapPolicy strictLabelCap = new CapPolicy(Integer.MAX_VALUE, 1);

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 10, strictLabelCap);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 2L);
		}

		@Test
		@DisplayName("같은(null 아닌) 레이블은 여전히 캡에 걸린다")
		void stillCapsSameNonNullLabel() {
			// given — 둘 다 labelId=100. maxPerLabel=1 이면 두 번째는 캡에 걸리고 대체 후보가 없어 결과가 1건이다
			ProductFeature first = feature(1L, 1L, 5L, 100L, 5.0);
			ProductFeature second = feature(2L, 2L, 6L, 100L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(first, second);
			List<ProductFeature> seeds = List.of(seed(10L, 5L), seed(11L, 6L));
			CapPolicy strictLabelCap = new CapPolicy(Integer.MAX_VALUE, 1);

			// when — pass2 는 캡 없이 나머지를 채우므로 size 를 1로 둬 pass2 가 끼어들지 않게 한다
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 1, strictLabelCap);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L);
		}

		@Test
		@DisplayName("pass1 이 캡 때문에 size 를 못 채우면 pass2 가 캡 없이 나머지를 채운다")
		void fillsShortfallWithPass2WhenCapBlocksPass1() {
			// given — 아티스트 5 인 후보 2개 뿐이고 maxPerArtist=1. pass1 은 1건만 뽑지만 size=2 라 pass2 가 나머지를 채운다
			ProductFeature first = feature(1L, 1L, 5L, 5.0);
			ProductFeature second = feature(2L, 2L, 5L, 4.0);
			Map<Long, ProductFeature> features = featuresOf(first, second);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));
			CapPolicy strictArtistCap = new CapPolicy(1, Integer.MAX_VALUE);

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 2, strictArtistCap);

			// then — 캡을 지키면 1건뿐이지만 2패스 폴백 덕에 결과가 2건 그대로 유지된다
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 2L);
		}

		@Test
		@DisplayName("UNCAPPED 정책은 캡 없이 앨범 dedup 결과 그대로 채운다")
		void uncappedPolicyAppliesNoCap() {
			// given — 아티스트 5 인 후보 3개. 아무리 캡이 없어도 앨범 dedup 은 그대로 적용된다
			ProductFeature candidateA = feature(1L, 1L, 5L, 5.0);
			ProductFeature candidateB = feature(2L, 2L, 5L, 4.0);
			ProductFeature candidateC = feature(3L, 3L, 5L, 3.0);
			Map<Long, ProductFeature> features = featuresOf(candidateA, candidateB, candidateC);
			List<ProductFeature> seeds = List.of(seed(10L, 5L));

			// when
			List<RankedCandidate> ranked = recommendRanker.rank(features, TasteSignal.empty(), seeds, Set.of(),
					Map.of(), Set.of(), 3, CapPolicy.UNCAPPED);

			// then
			assertThat(ranked).extracting(candidate -> candidate.feature().id()).containsExactly(1L, 2L, 3L);
		}
	}
}
