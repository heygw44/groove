package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.entity.Decade;

class ProductFeatureTest {

	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 5, 10, 0);

	@Nested
	@DisplayName("from()")
	class From {

		@Test
		@DisplayName("장르 CSV 를 id 집합으로, 발매 연도를 연대로 바꾼다")
		void parsesGenreCsvAndDecade() {
			// given
			ProductFeatureRow row = new ProductFeatureRow(1L, 10L, 20L, 1975, 4.5, CREATED_AT,
					ProductStatus.ON_SALE, "3,1,5");

			// when
			ProductFeature feature = ProductFeature.from(row);

			// then
			assertThat(feature.id()).isEqualTo(1L);
			assertThat(feature.artistId()).isEqualTo(10L);
			assertThat(feature.labelId()).isEqualTo(20L);
			assertThat(feature.genreIds()).containsExactlyInAnyOrder(1L, 3L, 5L);
			assertThat(feature.decade()).isEqualTo(Decade.D1970);
			assertThat(feature.averageRating()).isEqualTo(4.5);
			assertThat(feature.createdAt()).isEqualTo(CREATED_AT);
			assertThat(feature.hidden()).isFalse();
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" "})
		@DisplayName("장르 CSV 가 비어 있으면 빈 집합으로 만든다")
		void returnsEmptyGenreIdsWhenCsvBlank(String genreIds) {
			// given
			ProductFeatureRow row = new ProductFeatureRow(1L, 10L, null, null, null, CREATED_AT,
					ProductStatus.HIDDEN, genreIds);

			// when
			ProductFeature feature = ProductFeature.from(row);

			// then
			assertThat(feature.genreIds()).isEmpty();
			assertThat(feature.labelId()).isNull();
			assertThat(feature.decade()).isNull();
			assertThat(feature.hidden()).isTrue();
		}
	}
}
