package com.groove.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;

class ProductImageTest {

	@Nested
	@DisplayName("isThumbnail()")
	class IsThumbnail {

		@ParameterizedTest
		@DisplayName("sortOrder 가 0이면 대표 이미지다")
		@CsvSource({"0, true", "1, false", "5, false"})
		void returnsWhetherSortOrderIsZero(int sortOrder, boolean expected) {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			ProductImage image = ProductImage.of(product, "https://cdn.groove.com/cover.jpg", sortOrder);

			// when & then
			assertThat(image.isThumbnail()).isEqualTo(expected);
		}
	}
}
