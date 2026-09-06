package com.groove.product.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.product.entity.EditionType;

class ProductSearchRequestTest {

	@Nested
	@DisplayName("toCondition()")
	class ToCondition {

		@ParameterizedTest
		@DisplayName("keyword 패턴에 따라 barcode·catalogNoNormalized 조건을 판별한다")
		@CsvSource(nullValues = "NULL", value = {
			"8809012345678, 8809012345678, NULL",
			"CS-8163, NULL, CS8163",
			"'Kind of Blue', NULL, NULL",
			"1989, NULL, 1989"
		})
		void classifiesKeywordIntoBarcodeOrCatalogNo(String keyword, String expectedBarcode,
				String expectedCatalogNo) {
			// given
			ProductSearchRequest request = request(keyword);

			// when
			ProductSearchCondition condition = request.toCondition(null);

			// then
			assertThat(condition.keyword()).isEqualTo(keyword);
			assertThat(condition.barcode()).isEqualTo(expectedBarcode);
			assertThat(condition.catalogNoNormalized()).isEqualTo(expectedCatalogNo);
		}

		@Test
		@DisplayName("albumId·country·pressingYear·editionType 을 조건에 그대로 전달한다")
		void passesPressingFiltersIntoCondition() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, 3L, "US", 1959, 2000,
					EditionType.ORIGINAL, null, null, null, null, null);

			// when
			ProductSearchCondition condition = request.toCondition(null);

			// then
			assertThat(condition.albumId()).isEqualTo(3L);
			assertThat(condition.country()).isEqualTo("US");
			assertThat(condition.pressingYearFrom()).isEqualTo(1959);
			assertThat(condition.pressingYearTo()).isEqualTo(2000);
			assertThat(condition.editionType()).isEqualTo(EditionType.ORIGINAL);
		}

		private ProductSearchRequest request(String keyword) {
			return new ProductSearchRequest(keyword, null, null, null, null, null, null, null, null, null, null,
					null, null, null);
		}
	}
}
