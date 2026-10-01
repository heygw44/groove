package com.groove.global.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SliceResponseTest {

	@Nested
	@DisplayName("of()")
	class Of {

		@Test
		@DisplayName("size 보다 많이 조회됐으면 앞 size 개만 담고 hasNext 가 true 다")
		void trimsAndFlagsNextWhenOverFetched() {
			// given
			List<Integer> fetched = List.of(1, 2, 3);

			// when
			SliceResponse<Integer> response = SliceResponse.of(fetched, 1, 2);

			// then
			assertThat(response.content()).containsExactly(1, 2);
			assertThat(response.hasNext()).isTrue();
			assertThat(response.page()).isEqualTo(1);
			assertThat(response.size()).isEqualTo(2);
		}

		@Test
		@DisplayName("size 이하로 조회됐으면 그대로 담고 hasNext 가 false 다")
		void keepsAllWhenWithinSize() {
			// when
			SliceResponse<Integer> response = SliceResponse.of(List.of(1, 2), 0, 2);

			// then
			assertThat(response.content()).containsExactly(1, 2);
			assertThat(response.hasNext()).isFalse();
		}

		@Test
		@DisplayName("조회 결과가 비어 있으면 빈 content 와 hasNext false 다")
		void returnsEmptyWhenNothingFetched() {
			// when
			SliceResponse<Integer> response = SliceResponse.of(List.of(), 0, 20);

			// then
			assertThat(response.content()).isEmpty();
			assertThat(response.hasNext()).isFalse();
		}
	}
}
