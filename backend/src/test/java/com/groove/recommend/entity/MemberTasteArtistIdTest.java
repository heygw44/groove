package com.groove.recommend.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberTasteArtistIdTest {

	@Nested
	@DisplayName("equals()")
	class Equals {

		@Test
		@DisplayName("profileId 와 artistId 가 같으면 같다")
		void returnsTrueWhenFieldsAreEqual() {
			// given
			MemberTasteArtistId id1 = MemberTasteArtistId.of(1L, 2L);
			MemberTasteArtistId id2 = MemberTasteArtistId.of(1L, 2L);

			// when & then
			assertThat(id1).isEqualTo(id2);
		}

		@Test
		@DisplayName("artistId 가 다르면 다르다")
		void returnsFalseWhenArtistIdDiffers() {
			// given
			MemberTasteArtistId id1 = MemberTasteArtistId.of(1L, 2L);
			MemberTasteArtistId id2 = MemberTasteArtistId.of(1L, 3L);

			// when & then
			assertThat(id1).isNotEqualTo(id2);
		}

		@Test
		@DisplayName("null 과 다르다")
		void returnsFalseWhenComparedWithNull() {
			// given
			MemberTasteArtistId id = MemberTasteArtistId.of(1L, 2L);

			// when & then
			assertThat(id).isNotEqualTo(null);
		}

		@Test
		@DisplayName("다른 타입과 다르다")
		void returnsFalseWhenComparedWithDifferentType() {
			// given
			MemberTasteArtistId id = MemberTasteArtistId.of(1L, 2L);

			// when & then
			assertThat(id).isNotEqualTo("1-2");
		}
	}

	@Nested
	@DisplayName("hashCode()")
	class HashCode {

		@Test
		@DisplayName("profileId 와 artistId 가 같으면 해시코드도 같다")
		void returnsSameHashCodeWhenFieldsAreEqual() {
			// given
			MemberTasteArtistId id1 = MemberTasteArtistId.of(1L, 2L);
			MemberTasteArtistId id2 = MemberTasteArtistId.of(1L, 2L);

			// when & then
			assertThat(id1).hasSameHashCodeAs(id2);
		}
	}
}
