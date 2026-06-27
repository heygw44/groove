package com.groove.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MetricsScrapeSecretGuard — prod prometheus 노출 시 약한 스크레이프 비번 거부")
class MetricsScrapeSecretGuardTest {

    private static MetricsScrapeSecretGuard guard(MockEnvironment env) {
        return new MetricsScrapeSecretGuard(env);
    }

    @Test
    @DisplayName("prometheus 노출 + 데모 기본 비번('change-me-demo')이면 기동을 거부한다")
    void rejectsPlaceholderPasswordWhenExposed() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "health,prometheus")
                .withProperty("metrics.scrape.password", "change-me-demo");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics.scrape.password");
    }

    @Test
    @DisplayName("prometheus 노출 + 빈 비번이면 거부한다")
    void rejectsBlankPasswordWhenExposed() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "health,prometheus")
                .withProperty("metrics.scrape.password", "");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics.scrape.password");
    }

    @Test
    @DisplayName("와일드카드(*) 노출 + 약한 비번이면 거부한다")
    void rejectsPlaceholderPasswordWhenWildcardExposed() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "*")
                .withProperty("metrics.scrape.password", "change-me-demo");
        assertThatThrownBy(() -> guard(env).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metrics.scrape.password");
    }

    @Test
    @DisplayName("prometheus 미노출이면 약한 비번이어도 통과한다 (비번이 무의미)")
    void allowsWeakPasswordWhenNotExposed() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "health")
                .withProperty("metrics.scrape.password", "change-me-demo");
        assertThatCode(() -> guard(env).afterPropertiesSet())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prometheus 노출 + 강한 고유 비번이면 통과한다")
    void allowsStrongPasswordWhenExposed() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "health,prometheus")
                .withProperty("metrics.scrape.password", "kP9$mVx2Lr8QwZ7nTb4Hy6Fc");
        assertThatCode(() -> guard(env).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
