package com.groove.coupon.api.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponRateLimitProperties — 설정 검증")
class CouponRateLimitPropertiesTest {

    @Test
    @DisplayName("유효한 설정은 통과")
    void validConfig() {
        assertThatCode(() -> new CouponRateLimitProperties(
                new CouponRateLimitProperties.Policy(10L, Duration.ofMinutes(1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("issue 정책 누락 → 예외")
    void missingIssue_throws() {
        assertThatThrownBy(() -> new CouponRateLimitProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("capacity 0 이하 → 예외")
    void nonPositiveCapacity_throws() {
        assertThatThrownBy(() -> new CouponRateLimitProperties.Policy(0L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refill-period 가 0/음수/null → 예외")
    void invalidRefillPeriod_throws() {
        assertThatThrownBy(() -> new CouponRateLimitProperties.Policy(10L, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CouponRateLimitProperties.Policy(10L, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
