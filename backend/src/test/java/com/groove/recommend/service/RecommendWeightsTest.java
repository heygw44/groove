package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.recommend.dto.RecommendReason;

class RecommendWeightsTest {

	@Nested
	@DisplayName("생성자")
	class Constructor {

		@Test
		@DisplayName("전부 null 이면 기본 가중치로 채운다")
		void fillsDefaultsWhenAllNull() {
			// when
			RecommendWeights weights = new RecommendWeights(null, null, null, null, null, null, null, null, null);

			// then
			assertThat(weights.tasteArtist()).isEqualTo(5);
			assertThat(weights.sameArtist()).isEqualTo(4);
			assertThat(weights.tasteGenre()).isEqualTo(3);
			assertThat(weights.sameGenre()).isEqualTo(2);
			assertThat(weights.sameLabel()).isEqualTo(2);
			assertThat(weights.tasteDecade()).isEqualTo(1);
			assertThat(weights.sameDecade()).isEqualTo(1);
			assertThat(weights.coPurchase()).isEqualTo(2.0);
			assertThat(weights.tasteMatchThreshold()).isEqualTo(3.0);
		}

		@Test
		@DisplayName("일부 필드만 지정하면 나머지는 기본값을 유지한다")
		void keepsDefaultsForUnspecifiedFieldsOnPartialBinding() {
			// given & when
			RecommendWeights weights = new RecommendWeights(null, null, null, 9, null, null, null, null, null);

			// then
			assertThat(weights.sameGenre()).isEqualTo(9);
			assertThat(weights.tasteArtist()).isEqualTo(5);
			assertThat(weights.sameArtist()).isEqualTo(4);
			assertThat(weights.coPurchase()).isEqualTo(2.0);
		}

		@Test
		@DisplayName("DEFAULT 상수는 전부 기본값이다")
		void defaultConstantHasDefaultValues() {
			// when & then
			assertThat(RecommendWeights.DEFAULT.tasteArtist()).isEqualTo(5);
			assertThat(RecommendWeights.DEFAULT.coPurchase()).isEqualTo(2.0);
			assertThat(RecommendWeights.DEFAULT.tasteMatchThreshold()).isEqualTo(3.0);
		}
	}

	@Nested
	@DisplayName("weightOf()")
	class WeightOf {

		private final RecommendWeights weights = RecommendWeights.DEFAULT;

		@Test
		@DisplayName("콘텐츠 매칭 이유마다 해당 가중치를 반환한다")
		void returnsWeightForEachContentReason() {
			// when & then
			assertThat(weights.weightOf(RecommendReason.TASTE_ARTIST)).isEqualTo(5.0);
			assertThat(weights.weightOf(RecommendReason.SAME_ARTIST)).isEqualTo(4.0);
			assertThat(weights.weightOf(RecommendReason.TASTE_GENRE)).isEqualTo(3.0);
			assertThat(weights.weightOf(RecommendReason.SAME_GENRE)).isEqualTo(2.0);
			assertThat(weights.weightOf(RecommendReason.SAME_LABEL)).isEqualTo(2.0);
			assertThat(weights.weightOf(RecommendReason.TASTE_DECADE)).isEqualTo(1.0);
			assertThat(weights.weightOf(RecommendReason.SAME_DECADE)).isEqualTo(1.0);
		}

		@Test
		@DisplayName("가중치가 없는 이유를 물으면 예외를 던진다")
		void throwsForReasonWithoutWeight() {
			// when & then
			assertThatThrownBy(() -> weights.weightOf(RecommendReason.BOUGHT_TOGETHER))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("withZeroed()")
	class WithZeroed {

		@Test
		@DisplayName("해당 차원의 가중치만 0 으로 만들고 나머지는 그대로 둔다")
		void zeroesOnlyTargetDimension() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when
			RecommendWeights zeroed = weights.withZeroed(RecommendReason.SAME_ARTIST);

			// then
			assertThat(zeroed.sameArtist()).isZero();
			assertThat(zeroed.tasteArtist()).isEqualTo(weights.tasteArtist());
			assertThat(zeroed.sameGenre()).isEqualTo(weights.sameGenre());
		}

		@Test
		@DisplayName("BOUGHT_TOGETHER 를 지정하면 공동구매 가중치를 0 으로 만든다")
		void zeroesCoPurchaseForBoughtTogether() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when
			RecommendWeights zeroed = weights.withZeroed(RecommendReason.BOUGHT_TOGETHER);

			// then
			assertThat(zeroed.coPurchase()).isZero();
			assertThat(zeroed.sameArtist()).isEqualTo(weights.sameArtist());
		}
	}

	@Nested
	@DisplayName("withCoPurchase()")
	class WithCoPurchase {

		@Test
		@DisplayName("공동구매 가중치만 바꾸고 콘텐츠 차원은 그대로 둔다")
		void changesOnlyCoPurchase() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when
			RecommendWeights changed = weights.withCoPurchase(7.5);

			// then
			assertThat(changed.coPurchase()).isEqualTo(7.5);
			assertThat(changed.tasteArtist()).isEqualTo(weights.tasteArtist());
			assertThat(changed.tasteMatchThreshold()).isEqualTo(weights.tasteMatchThreshold());
		}
	}

	@Nested
	@DisplayName("with()")
	class With {

		@Test
		@DisplayName("지정한 차원의 가중치만 바꾼 사본을 반환한다")
		void changesOnlyTargetDimension() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when
			RecommendWeights swept = weights.with(RecommendReason.TASTE_GENRE, 10);

			// then
			assertThat(swept.tasteGenre()).isEqualTo(10);
			assertThat(swept.sameGenre()).isEqualTo(weights.sameGenre());
			assertThat(swept.coPurchase()).isEqualTo(weights.coPurchase());
		}

		@Test
		@DisplayName("모든 콘텐츠 차원에 대해 값을 바꿀 수 있다")
		void changesEveryContentDimension() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when & then
			assertThat(weights.with(RecommendReason.TASTE_ARTIST, 1).tasteArtist()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.SAME_ARTIST, 1).sameArtist()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.TASTE_GENRE, 1).tasteGenre()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.SAME_GENRE, 1).sameGenre()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.SAME_LABEL, 1).sameLabel()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.TASTE_DECADE, 1).tasteDecade()).isEqualTo(1);
			assertThat(weights.with(RecommendReason.SAME_DECADE, 1).sameDecade()).isEqualTo(1);
		}

		@Test
		@DisplayName("가중치가 없는 이유를 바꾸려 하면 예외를 던진다")
		void throwsForReasonWithoutWeight() {
			// given
			RecommendWeights weights = RecommendWeights.DEFAULT;

			// when & then
			assertThatThrownBy(() -> weights.with(RecommendReason.POPULAR, 1))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
