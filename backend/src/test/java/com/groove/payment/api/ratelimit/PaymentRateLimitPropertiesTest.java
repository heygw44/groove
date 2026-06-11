package com.groove.payment.api.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentRateLimitProperties — 설정 검증")
class PaymentRateLimitPropertiesTest {

    @Test
    @DisplayName("유효한 설정은 통과")
    void validConfig() {
        assertThatCode(() -> new PaymentRateLimitProperties(
                new PaymentRateLimitProperties.Policy(5L, Duration.ofMinutes(1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("post 정책 누락 → 예외")
    void missingPost_throws() {
        assertThatThrownBy(() -> new PaymentRateLimitProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("capacity 0/음수 → 예외")
    void nonPositiveCapacity_throws() {
        assertThatThrownBy(() -> new PaymentRateLimitProperties.Policy(0L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentRateLimitProperties.Policy(-1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refill-period 가 0/음수/null → 예외")
    void invalidRefillPeriod_throws() {
        assertThatThrownBy(() -> new PaymentRateLimitProperties.Policy(5L, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentRateLimitProperties.Policy(5L, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentRateLimitProperties.Policy(5L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
