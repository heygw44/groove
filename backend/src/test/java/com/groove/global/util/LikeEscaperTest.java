package com.groove.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LikeEscaperTest {

	@Nested
	@DisplayName("escape()")
	class Escape {

		@Test
		@DisplayName("null 이면 null 을 반환한다")
		void returnsNullForNull() {
			// when & then
			assertThat(LikeEscaper.escape(null)).isNull();
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', value = {
			"miles davis|miles davis",
			"100%|100!%",
			"snake_eyes|snake!_eyes",
			"wow!|wow!!",
			"50%_off!|50!%!_off!!",
			"%%|!%!%",
			"!%|!!!%"
		})
		@DisplayName("%, _, ! 를 ! 로 이스케이프한다")
		void escapesWildcardsAndEscapeChar(String raw, String expected) {
			// when & then
			assertThat(LikeEscaper.escape(raw)).isEqualTo(expected);
		}
	}
}
