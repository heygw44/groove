package com.groove.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TokenHasher 단위 테스트")
class TokenHasherTest {

    @Test
    @DisplayName("SHA-256 hex 출력은 64자 소문자")
    void sha256_outputs64LowercaseHexChars() {
        String hash = TokenHasher.sha256Hex("any-token-string");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("동일 입력 → 동일 해시 (결정적)")
    void sha256_isDeterministic() {
        String input = "deterministic-input";

        assertThat(TokenHasher.sha256Hex(input))
                .isEqualTo(TokenHasher.sha256Hex(input));
    }

    @Test
    @DisplayName("서로 다른 입력 → 서로 다른 해시")
    void sha256_differsForDifferentInputs() {
        assertThat(TokenHasher.sha256Hex("token-a"))
                .isNotEqualTo(TokenHasher.sha256Hex("token-b"));
    }

    @Test
    @DisplayName("RFC 6234 표준 벡터 — 빈 문자열의 SHA-256")
    void sha256_emptyString_matchesStandardVector() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertThat(TokenHasher.sha256Hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    @DisplayName("표준 벡터 — \"abc\" 의 SHA-256 (RFC 6234 §A.1)")
    void sha256_abc_matchesStandardVector() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("마침표 포함 긴 문자열도 64자 hex 출력 (실제 토큰 길이 시뮬레이션)")
    void sha256_dottedLongInput_returnsHex64() {
        String input = ("a".repeat(40)) + "." + ("b".repeat(80)) + "." + ("c".repeat(43));

        String hash = TokenHasher.sha256Hex(input);

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
    }
}
