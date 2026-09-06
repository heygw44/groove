package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.groove.recommend.dto.RecommendReason;
import com.groove.recommend.entity.Decade;
import com.groove.recommend.service.RecommendScorer.ScoreResult;

class RecommendScorerTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 10, 0);

	private final RecommendScorer recommendScorer = new RecommendScorer();

	private ProductFeature candidate() {
		return feature(100L, 1L, 10L, Set.of(1L, 2L), Decade.D1990);
	}

	private ProductFeature feature(Long id, Long artistId, Long labelId, Set<Long> genreIds, Decade decade) {
		return new ProductFeature(id, artistId, labelId, genreIds, decade, 4.0, NOW, false);
	}

	@Nested
	@DisplayName("score()")
	class Score {

		static Stream<Arguments> tasteAndSeedCombinations() {
			return Stream.of(
					Arguments.of(TasteSignal.empty(), List.of(), 0),
					Arguments.of(new TasteSignal(Set.of(1L), Set.of(), Set.of()), List.of(), 5),
					Arguments.of(new TasteSignal(Set.of(), Set.of(2L), Set.of()), List.of(), 3),
					Arguments.of(new TasteSignal(Set.of(1L), Set.of(2L), Set.of()), List.of(), 8));
		}

		@ParameterizedTest
		@MethodSource("tasteAndSeedCombinations")
		@DisplayName("취향 신호 조합에 따라 콘텐츠 점수를 합산한다")
		void sumsContentScoreByTasteSignals(TasteSignal taste, List<ProductFeature> seeds, int expectedScore) {
			// when
			ScoreResult result = recommendScorer.score(candidate(), taste, seeds, Set.of(), 0);

			// then
			assertThat(result.contentScore()).isEqualTo(expectedScore);
		}

		@Test
		@DisplayName("같은 아티스트·레이블 시드가 있으면 SAME_ARTIST 와 SAME_LABEL 을 합산한다")
		void addsSameArtistAndSameLabel() {
			// given
			List<ProductFeature> seeds = List.of(
					feature(1L, 1L, 20L, Set.of(9L), Decade.D2000),
					feature(2L, 2L, 10L, Set.of(9L), Decade.D2000));

			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), seeds, Set.of(), 0);

			// then
			assertThat(result.contentScore()).isEqualTo(6);
			assertThat(result.contributions())
					.containsEntry(RecommendReason.SAME_ARTIST, 4.0)
					.containsEntry(RecommendReason.SAME_LABEL, 2.0);
		}

		@Test
		@DisplayName("취향·시드 신호가 전부 매칭되면 콘텐츠 점수는 18 점이다")
		void sumsAllContentReasons() {
			// given
			TasteSignal taste = new TasteSignal(Set.of(1L), Set.of(2L), Set.of(Decade.D1990));
			List<ProductFeature> seeds = List.of(feature(1L, 1L, 10L, Set.of(2L), Decade.D1990));

			// when
			ScoreResult result = recommendScorer.score(candidate(), taste, seeds, Set.of(), 0);

			// then
			assertThat(result.contentScore()).isEqualTo(18);
		}

		@Test
		@DisplayName("같은 아티스트 시드가 여러 개여도 SAME_ARTIST 는 1회만 가산한다")
		void addsSameArtistOnlyOncePerCandidate() {
			// given
			List<ProductFeature> seeds = List.of(
					feature(1L, 1L, 99L, Set.of(), null),
					feature(2L, 1L, 99L, Set.of(), null),
					feature(3L, 1L, 99L, Set.of(), null));

			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), seeds, Set.of(), 0);

			// then
			assertThat(result.contentScore()).isEqualTo(4);
			assertThat(result.contributions()).containsEntry(RecommendReason.SAME_ARTIST, 4.0);
		}

		@Test
		@DisplayName("매칭 시드가 최근 본 상품뿐이면 SAME_ARTIST 대신 RECENTLY_VIEWED_SIMILAR 로 표기한다")
		void replacesReasonWhenOnlyRecentViewMatches() {
			// given
			ProductFeature recentOnlySeed = feature(1L, 1L, 99L, Set.of(), null);

			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), List.of(recentOnlySeed),
					Set.of(1L), 0);

			// then
			assertThat(result.contributions())
					.doesNotContainKey(RecommendReason.SAME_ARTIST)
					.containsEntry(RecommendReason.RECENTLY_VIEWED_SIMILAR, 4.0);
		}

		@Test
		@DisplayName("최근 본 상품 시드와 위시 시드가 함께 매칭되면 SAME_ARTIST 그대로 표기한다")
		void keepsSameReasonWhenNonRecentSeedAlsoMatches() {
			// given
			ProductFeature recentSeed = feature(1L, 1L, 99L, Set.of(), null);
			ProductFeature wishlistSeed = feature(2L, 1L, 99L, Set.of(), null);

			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(),
					List.of(recentSeed, wishlistSeed), Set.of(1L), 0);

			// then
			assertThat(result.contributions())
					.containsEntry(RecommendReason.SAME_ARTIST, 4.0)
					.doesNotContainKey(RecommendReason.RECENTLY_VIEWED_SIMILAR);
		}

		@Test
		@DisplayName("여러 차원이 최근 본 상품으로만 치환되면 RECENTLY_VIEWED_SIMILAR 기여도가 합산된다")
		void sumsRecentlyViewedContributionsAcrossDimensions() {
			// given
			ProductFeature recentOnlySeed = feature(1L, 1L, 99L, Set.of(2L), null);

			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), List.of(recentOnlySeed),
					Set.of(1L), 0);

			// then
			assertThat(result.contributions()).containsEntry(RecommendReason.RECENTLY_VIEWED_SIMILAR, 6.0);
		}

		@Test
		@DisplayName("공동구매 횟수가 있으면 BOUGHT_TOGETHER 기여도를 더하고 최종 점수에 합산한다")
		void addsBoughtTogetherToTotalScore() {
			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), List.of(), Set.of(), 3);

			// then
			assertThat(result.contributions()).containsEntry(RecommendReason.BOUGHT_TOGETHER, 6.0);
			assertThat(result.totalScore()).isEqualTo(result.contentScore() + 6.0);
		}

		@Test
		@DisplayName("공동구매 횟수가 0 이면 BOUGHT_TOGETHER 이유를 넣지 않는다")
		void omitsBoughtTogetherWhenCountIsZero() {
			// when
			ScoreResult result = recommendScorer.score(candidate(), TasteSignal.empty(), List.of(), Set.of(), 0);

			// then
			assertThat(result.contributions()).doesNotContainKey(RecommendReason.BOUGHT_TOGETHER);
		}

		@Test
		@DisplayName("후보의 레이블이 없으면 SAME_LABEL 매칭이 없다")
		void skipsSameLabelWhenCandidateLabelIsNull() {
			// given
			ProductFeature candidateWithoutLabel = feature(100L, 1L, null, Set.of(), null);
			List<ProductFeature> seeds = List.of(feature(1L, 5L, 10L, Set.of(), null));

			// when
			ScoreResult result = recommendScorer.score(candidateWithoutLabel, TasteSignal.empty(), seeds, Set.of(),
					0);

			// then
			assertThat(result.contributions()).doesNotContainKey(RecommendReason.SAME_LABEL);
		}

		@Test
		@DisplayName("후보의 연대가 없으면 SAME_DECADE 매칭이 없다")
		void skipsSameDecadeWhenCandidateDecadeIsNull() {
			// given
			ProductFeature candidateWithoutDecade = feature(100L, 1L, 10L, Set.of(), null);
			List<ProductFeature> seeds = List.of(feature(1L, 5L, 20L, Set.of(), Decade.D1990));

			// when
			ScoreResult result = recommendScorer.score(candidateWithoutDecade, TasteSignal.empty(), seeds, Set.of(),
					0);

			// then
			assertThat(result.contributions()).doesNotContainKey(RecommendReason.SAME_DECADE);
		}
	}

	@Nested
	@DisplayName("scoreTaste()")
	class ScoreTaste {

		@Test
		@DisplayName("시드·공동구매 없이 취향 점수만 계산한다")
		void scoresWithoutSeedsOrCoPurchase() {
			// given
			TasteSignal taste = new TasteSignal(Set.of(1L), Set.of(), Set.of());

			// when
			ScoreResult result = recommendScorer.scoreTaste(candidate(), taste);

			// then
			assertThat(result.contentScore()).isEqualTo(5);
			assertThat(result.totalScore()).isEqualTo(5.0);
			assertThat(result.contributions()).doesNotContainKey(RecommendReason.BOUGHT_TOGETHER);
		}
	}

	@Nested
	@DisplayName("topReasons()")
	class TopReasons {

		@Test
		@DisplayName("기여도가 큰 순으로 최대 2개만 반환한다")
		void returnsTopTwoByContribution() {
			// given
			Map<RecommendReason, Double> contributions = new EnumMap<>(RecommendReason.class);
			contributions.put(RecommendReason.SAME_ARTIST, 4.0);
			contributions.put(RecommendReason.TASTE_GENRE, 3.0);
			contributions.put(RecommendReason.SAME_GENRE, 2.0);
			ScoreResult result = new ScoreResult(9, 9.0, contributions);

			// when
			List<RecommendReason> reasons = result.topReasons();

			// then
			assertThat(reasons).containsExactly(RecommendReason.SAME_ARTIST, RecommendReason.TASTE_GENRE);
		}

		@Test
		@DisplayName("기여도가 동률이면 enum 선언 순으로 정렬한다")
		void breaksTieByDeclarationOrder() {
			// given
			Map<RecommendReason, Double> contributions = new EnumMap<>(RecommendReason.class);
			contributions.put(RecommendReason.SAME_LABEL, 3.0);
			contributions.put(RecommendReason.TASTE_GENRE, 3.0);
			contributions.put(RecommendReason.SAME_DECADE, 3.0);
			ScoreResult result = new ScoreResult(9, 9.0, contributions);

			// when
			List<RecommendReason> reasons = result.topReasons();

			// then
			assertThat(reasons).containsExactly(RecommendReason.TASTE_GENRE, RecommendReason.SAME_LABEL);
		}

		@Test
		@DisplayName("기여도가 0 이하인 이유는 제외한다")
		void excludesNonPositiveContributions() {
			// given
			Map<RecommendReason, Double> contributions = new EnumMap<>(RecommendReason.class);
			contributions.put(RecommendReason.SAME_ARTIST, 0.0);
			ScoreResult result = new ScoreResult(0, 0.0, contributions);

			// when
			List<RecommendReason> reasons = result.topReasons();

			// then
			assertThat(reasons).isEmpty();
		}

		@Test
		@DisplayName("이유가 없으면 빈 리스트를 반환한다")
		void returnsEmptyWhenNoContributions() {
			// given
			ScoreResult result = new ScoreResult(0, 0.0, new EnumMap<>(RecommendReason.class));

			// when
			List<RecommendReason> reasons = result.topReasons();

			// then
			assertThat(reasons).isEmpty();
		}
	}

	@Nested
	@DisplayName("matchesTaste()")
	class MatchesTaste {

		@Test
		@DisplayName("TASTE_DECADE 만 매칭되면 임계값 미만이라 false 를 반환한다")
		void returnsFalseWhenOnlyDecadeMatches() {
			// given
			TasteSignal taste = new TasteSignal(Set.of(), Set.of(), Set.of(Decade.D1990));

			// when
			ScoreResult result = recommendScorer.scoreTaste(candidate(), taste);

			// then
			assertThat(recommendScorer.matchesTaste(result)).isFalse();
		}

		@Test
		@DisplayName("TASTE_GENRE 가 매칭되면 임계값을 충족해 true 를 반환한다")
		void returnsTrueWhenGenreMatches() {
			// given
			TasteSignal taste = new TasteSignal(Set.of(), Set.of(2L), Set.of());

			// when
			ScoreResult result = recommendScorer.scoreTaste(candidate(), taste);

			// then
			assertThat(recommendScorer.matchesTaste(result)).isTrue();
		}

		@Test
		@DisplayName("TASTE_ARTIST 가 매칭되면 true 를 반환한다")
		void returnsTrueWhenArtistMatches() {
			// given
			TasteSignal taste = new TasteSignal(Set.of(1L), Set.of(), Set.of());

			// when
			ScoreResult result = recommendScorer.scoreTaste(candidate(), taste);

			// then
			assertThat(recommendScorer.matchesTaste(result)).isTrue();
		}
	}
}
