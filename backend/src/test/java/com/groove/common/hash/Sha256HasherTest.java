package com.groove.common.hash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sha256Hasher 단위 테스트")
class Sha256HasherTest {

    @Test
    @DisplayName("SHA-256 hex 출력은 64자 소문자")
    void hex_outputs64LowercaseHexChars() {
        String hash = Sha256Hasher.hex("any-input-string");

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("동일 입력 → 동일 해시 (결정적)")
    void hex_isDeterministic() {
        assertThat(Sha256Hasher.hex("same")).isEqualTo(Sha256Hasher.hex("same"));
    }

    @Test
    @DisplayName("표준 벡터 — \"abc\" 의 SHA-256 (RFC 6234 §A.1)")
    void hex_abc_matchesStandardVector() {
        assertThat(Sha256Hasher.hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("표준 벡터 — 빈 문자열의 SHA-256")
    void hex_emptyString_matchesStandardVector() {
        assertThat(Sha256Hasher.hex(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
