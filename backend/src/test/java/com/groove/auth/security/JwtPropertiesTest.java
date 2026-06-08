package com.groove.auth.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProperties — 바인딩 검증")
class JwtPropertiesTest {

    private static final String VALID_SECRET = "test-only-secret-key-minimum-256-bits-long-for-jwt-signing";

    @Test
    @DisplayName("정상 값은 그대로 통과한다")
    void validValues_pass() {
        assertThatCode(() -> new JwtProperties(VALID_SECRET, 1800, 1209600))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null 시크릿은 실패")
    void nullSecret_throws() {
        assertThatThrownBy(() -> new JwtProperties(null, 1800, 1209600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret");
    }

    @Test
    @DisplayName("32바이트 미만 시크릿은 실패")
    void tooShortSecret_throws() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", 1800, 1209600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName(".env.example 의 플레이스홀더는 길이를 통과해도 거부한다 (이슈 #165)")
    void placeholderSecret_throws() {
        assertThatThrownBy(() -> new JwtProperties(
                "change-this-to-a-256-bit-secret-key-in-production", 1800, 1209600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".env.example");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("access-token-ttl-seconds 가 0 이하면 실패")
    void nonPositiveAccessTtl_throws(long ttl) {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, ttl, 1209600))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access-token-ttl-seconds");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("refresh-token-ttl-seconds 가 0 이하면 실패")
    void nonPositiveRefreshTtl_throws(long ttl) {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, 1800, ttl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refresh-token-ttl-seconds");
    }
}
