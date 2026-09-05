package com.groove.product.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

class ProductSortTypeTest {

	@Nested
	@DisplayName("from()")
	class From {

		@ParameterizedTest
		@CsvSource({
			"latest, LATEST",
			"priceAsc, PRICE_ASC",
			"priceDesc, PRICE_DESC",
			"rating, RATING",
			"popular, POPULAR"
		})
		@DisplayName("정의된 값이면 대응하는 정렬 기준을 반환한다")
		void returnsMatchingSortType(String value, ProductSortType expected) {
			// when & then
			assertThat(ProductSortType.from(value)).isEqualTo(expected);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" "})
		@DisplayName("null 이거나 공백이면 LATEST 를 반환한다")
		void returnsLatestWhenBlank(String value) {
			// when & then
			assertThat(ProductSortType.from(value)).isEqualTo(ProductSortType.LATEST);
		}

		@Test
		@DisplayName("정의되지 않은 값이면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenUnknownValue() {
			// when & then
			assertThatThrownBy(() -> ProductSortType.from("unknown"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}
}
