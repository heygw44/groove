package com.groove.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecretPlaceholderGuard — .env.example 플레이스홀더 거부")
class SecretPlaceholderGuardTest {

    @Test
    @DisplayName(".env.example 의 JWT 플레이스홀더는 거부한다")
    void rejectsKnownJwtPlaceholder() {
        assertThatThrownBy(() -> SecretPlaceholderGuard.rejectPlaceholder(
                "jwt.secret", "change-this-to-a-256-bit-secret-key-in-production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret")
                .hasMessageContaining(".env.example");
    }

    @Test
    @DisplayName(".env.example 의 웹훅 플레이스홀더는 거부한다")
    void rejectsKnownWebhookPlaceholder() {
        assertThatThrownBy(() -> SecretPlaceholderGuard.rejectPlaceholder(
                "payment.mock.webhook-secret", "change-this-mock-webhook-secret-in-production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.mock.webhook-secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "change-this-anything-else",       // change-this 마커
            "please-change-me-now",            // change-me 마커
            "my-changeme-secret",              // changeme 마커
            "CHANGE-THIS-UPPERCASE-VARIANT",   // 대소문자 무시
            "  change-this-with-spaces  ",     // 앞뒤 공백 무시
    })
    @DisplayName("마커 부분문자열을 포함하면 거부한다 (대소문자·공백 무시)")
    void rejectsMarkerSubstrings(String secret) {
        assertThatThrownBy(() -> SecretPlaceholderGuard.rejectPlaceholder("some.secret", secret))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "local-dev-secret-key-minimum-256-bits-long",            // application-local.yaml
            "local-dev-mock-webhook-secret",                         // application-local.yaml
            "test-only-secret-key-minimum-256-bits-long-for-jwt-signing", // application-test.yaml
            "test-mock-webhook-secret",                              // application-test.yaml
            "k3J9xQ2mZ7pL5vR8nT1wY6bH4cF0sD",                       // 무작위 운영 시크릿
    })
    @DisplayName("local/test 더미값과 정상 운영 시크릿은 통과한다")
    void allowsLegitimateSecrets(String secret) {
        assertThatCode(() -> SecretPlaceholderGuard.rejectPlaceholder("some.secret", secret))
                .doesNotThrowAnyException();
    }
}
