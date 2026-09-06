package com.groove.recommend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberTasteGenreIdTest {

	@Nested
	@DisplayName("equals()")
	class Equals {

		@Test
		@DisplayName("profileId 와 genreId 가 같으면 같다")
		void returnsTrueWhenFieldsAreEqual() {
			// given
			MemberTasteGenreId id1 = MemberTasteGenreId.of(1L, 2L);
			MemberTasteGenreId id2 = MemberTasteGenreId.of(1L, 2L);

			// when & then
			assertThat(id1).isEqualTo(id2);
		}

		@Test
		@DisplayName("genreId 가 다르면 다르다")
		void returnsFalseWhenGenreIdDiffers() {
			// given
			MemberTasteGenreId id1 = MemberTasteGenreId.of(1L, 2L);
			MemberTasteGenreId id2 = MemberTasteGenreId.of(1L, 3L);

			// when & then
			assertThat(id1).isNotEqualTo(id2);
		}

		@Test
		@DisplayName("null 과 다르다")
		void returnsFalseWhenComparedWithNull() {
			// given
			MemberTasteGenreId id = MemberTasteGenreId.of(1L, 2L);

			// when & then
			assertThat(id).isNotEqualTo(null);
		}

		@Test
		@DisplayName("다른 타입과 다르다")
		void returnsFalseWhenComparedWithDifferentType() {
			// given
			MemberTasteGenreId id = MemberTasteGenreId.of(1L, 2L);

			// when & then
			assertThat(id).isNotEqualTo("1-2");
		}
	}

	@Nested
	@DisplayName("hashCode()")
	class HashCode {

		@Test
		@DisplayName("profileId 와 genreId 가 같으면 해시코드도 같다")
		void returnsSameHashCodeWhenFieldsAreEqual() {
			// given
			MemberTasteGenreId id1 = MemberTasteGenreId.of(1L, 2L);
			MemberTasteGenreId id2 = MemberTasteGenreId.of(1L, 2L);

			// when & then
			assertThat(id1).hasSameHashCodeAs(id2);
		}
	}
}
