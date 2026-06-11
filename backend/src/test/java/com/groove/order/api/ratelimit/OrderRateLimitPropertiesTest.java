package com.groove.order.api.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderRateLimitProperties — 설정 검증")
class OrderRateLimitPropertiesTest {

    @Test
    @DisplayName("유효한 설정은 통과")
    void validConfig() {
        assertThatCode(() -> new OrderRateLimitProperties(
                new OrderRateLimitProperties.Policy(10L, Duration.ofMinutes(1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("guest-lookup 정책 누락 → 예외")
    void missingGuestLookup_throws() {
        assertThatThrownBy(() -> new OrderRateLimitProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("capacity 0/음수 → 예외")
    void nonPositiveCapacity_throws() {
        assertThatThrownBy(() -> new OrderRateLimitProperties.Policy(0L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OrderRateLimitProperties.Policy(-1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refill-period 가 0/음수/null → 예외")
    void invalidRefillPeriod_throws() {
        assertThatThrownBy(() -> new OrderRateLimitProperties.Policy(10L, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OrderRateLimitProperties.Policy(10L, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OrderRateLimitProperties.Policy(10L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
