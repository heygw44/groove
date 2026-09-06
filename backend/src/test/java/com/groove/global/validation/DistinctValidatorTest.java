package com.groove.global.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DistinctValidatorTest {

	private final DistinctValidator validator = new DistinctValidator();

	@Nested
	@DisplayName("isValid()")
	class IsValid {

		@Test
		@DisplayName("중복이 없으면 통과시킨다")
		void returnsTrueWhenDistinct() {
			// when
			boolean result = validator.isValid(List.of(1L, 2L, 3L), null);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("빈 컬렉션은 통과시킨다")
		void returnsTrueWhenEmpty() {
			// when
			boolean result = validator.isValid(List.of(), null);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("중복이 있으면 거른다")
		void returnsFalseWhenDuplicated() {
			// when
			boolean result = validator.isValid(List.of(1L, 2L, 1L), null);

			// then
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("null 컬렉션은 @NotNull 이 볼 몫이라 통과시킨다")
		void returnsTrueWhenNull() {
			// when
			boolean result = validator.isValid(null, null);

			// then
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("원소에 null 이 섞여 있어도 예외 없이 중복을 판단한다")
		void handlesNullElements() {
			// given
			List<Long> values = Arrays.asList((Long)null, null);

			// when
			boolean result = validator.isValid(values, null);

			// then
			assertThat(result).isFalse();
		}
	}
}
