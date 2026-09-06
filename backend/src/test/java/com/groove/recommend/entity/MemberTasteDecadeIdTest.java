package com.groove.recommend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberTasteDecadeIdTest {

	@Nested
	@DisplayName("equals()")
	class Equals {

		@Test
		@DisplayName("profileId 와 decade 가 같으면 같다")
		void returnsTrueWhenFieldsAreEqual() {
			// given
			MemberTasteDecadeId id1 = MemberTasteDecadeId.of(1L, Decade.D1970);
			MemberTasteDecadeId id2 = MemberTasteDecadeId.of(1L, Decade.D1970);

			// when & then
			assertThat(id1).isEqualTo(id2);
		}

		@Test
		@DisplayName("decade 가 다르면 다르다")
		void returnsFalseWhenDecadeDiffers() {
			// given
			MemberTasteDecadeId id1 = MemberTasteDecadeId.of(1L, Decade.D1970);
			MemberTasteDecadeId id2 = MemberTasteDecadeId.of(1L, Decade.D1980);

			// when & then
			assertThat(id1).isNotEqualTo(id2);
		}

		@Test
		@DisplayName("null 과 다르다")
		void returnsFalseWhenComparedWithNull() {
			// given
			MemberTasteDecadeId id = MemberTasteDecadeId.of(1L, Decade.D1970);

			// when & then
			assertThat(id).isNotEqualTo(null);
		}

		@Test
		@DisplayName("다른 타입과 다르다")
		void returnsFalseWhenComparedWithDifferentType() {
			// given
			MemberTasteDecadeId id = MemberTasteDecadeId.of(1L, Decade.D1970);

			// when & then
			assertThat(id).isNotEqualTo("1-D1970");
		}
	}

	@Nested
	@DisplayName("hashCode()")
	class HashCode {

		@Test
		@DisplayName("profileId 와 decade 가 같으면 해시코드도 같다")
		void returnsSameHashCodeWhenFieldsAreEqual() {
			// given
			MemberTasteDecadeId id1 = MemberTasteDecadeId.of(1L, Decade.D1970);
			MemberTasteDecadeId id2 = MemberTasteDecadeId.of(1L, Decade.D1970);

			// when & then
			assertThat(id1).hasSameHashCodeAs(id2);
		}
	}
}
