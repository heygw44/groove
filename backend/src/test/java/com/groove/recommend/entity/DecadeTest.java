package com.groove.recommend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DecadeTest {

	@Nested
	@DisplayName("fromYear()")
	class FromYear {

		@ParameterizedTest
		@CsvSource({
					"1960, D1960",
					"1975, D1970",
					"2019, D2010",
					"2020, D2020",
					"2031, D2020"
		})
		@DisplayName("연도에 해당하는 연대를 반환한다")
		void returnsDecadeForYear(Integer year, Decade expected) {
			// when
			Decade actual = Decade.fromYear(year);

			// then
			assertThat(actual).isEqualTo(expected);
		}

		@Test
		@DisplayName("1960 미만 연도면 null 을 반환한다")
		void returnsNullWhenYearBefore1960() {
			// when
			Decade actual = Decade.fromYear(1959);

			// then
			assertThat(actual).isNull();
		}

		@Test
		@DisplayName("연도가 없으면 null 을 반환한다")
		void returnsNullWhenYearIsNull() {
			// when
			Decade actual = Decade.fromYear(null);

			// then
			assertThat(actual).isNull();
		}
	}
}
