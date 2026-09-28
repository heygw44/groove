package com.groove.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class VirtualAccountSecretHasherTest {

	@Nested
	@DisplayName("hash()")
	class Hash {

		@Test
		@DisplayName("같은 문자열은 같은 해시를 만들고 평문과 다르다")
		void hashesDeterministicallyAndDiffersFromPlainText() {
			// when
			String hash = VirtualAccountSecretHasher.hash("va-secret");

			// then
			assertThat(hash).isEqualTo(VirtualAccountSecretHasher.hash("va-secret"));
			assertThat(hash).isNotEqualTo("va-secret").hasSize(64);
		}

		@Test
		@DisplayName("null 이면 null 을 반환한다")
		void returnsNullForNullInput() {
			assertThat(VirtualAccountSecretHasher.hash(null)).isNull();
		}

		@Test
		@DisplayName("SHA-256 알고리즘을 쓸 수 없으면 IllegalStateException 으로 감싼다")
		void wrapsNoSuchAlgorithmException() {
			try (MockedStatic<MessageDigest> mocked = Mockito.mockStatic(MessageDigest.class)) {
				mocked.when(() -> MessageDigest.getInstance("SHA-256"))
						.thenThrow(new NoSuchAlgorithmException("no SHA-256"));

				assertThatThrownBy(() -> VirtualAccountSecretHasher.hash("va-secret"))
						.isInstanceOf(IllegalStateException.class)
						.hasCauseInstanceOf(NoSuchAlgorithmException.class);
			}
		}
	}

	@Nested
	@DisplayName("matches()")
	class Matches {

		@Test
		@DisplayName("평문 해시가 저장된 해시와 같으면 true 다")
		void returnsTrueWhenHashesMatch() {
			// given
			String storedHash = VirtualAccountSecretHasher.hash("va-secret");

			// when & then
			assertThat(VirtualAccountSecretHasher.matches("va-secret", storedHash)).isTrue();
		}

		@Test
		@DisplayName("평문이 다르면 false 다")
		void returnsFalseWhenSecretDiffers() {
			// given
			String storedHash = VirtualAccountSecretHasher.hash("va-secret");

			// when & then
			assertThat(VirtualAccountSecretHasher.matches("다른-secret", storedHash)).isFalse();
		}

		@Test
		@DisplayName("secret 이 null 이면 false 다")
		void returnsFalseWhenSecretIsNull() {
			assertThat(VirtualAccountSecretHasher.matches(null, "hash")).isFalse();
		}

		@Test
		@DisplayName("저장된 해시가 null 이면 false 다")
		void returnsFalseWhenStoredHashIsNull() {
			assertThat(VirtualAccountSecretHasher.matches("va-secret", null)).isFalse();
		}
	}
}
