package com.groove.auth.security.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimitPropertiesTest {

    @Test
    void buildsWithValidValues() {
        AuthRateLimitProperties.Policy login =
                new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(1));
        AuthRateLimitProperties.Policy signup =
                new AuthRateLimitProperties.Policy(3L, Duration.ofMinutes(1));

        AuthRateLimitProperties properties = new AuthRateLimitProperties(login, signup);

        assertThat(properties.login().capacity()).isEqualTo(10L);
        assertThat(properties.login().refillPeriod()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.signup().capacity()).isEqualTo(3L);
        assertThat(properties.signup().refillPeriod()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rejectsNullPolicies() {
        AuthRateLimitProperties.Policy login =
                new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(1));

        assertThatThrownBy(() -> new AuthRateLimitProperties(null, login))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login");
        assertThatThrownBy(() -> new AuthRateLimitProperties(login, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signup");
    }

    @Test
    void policyRejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(0L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");
        assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(-1L, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capacity");
    }

    @Test
    void policyRejectsNullOrZeroRefillPeriod() {
        assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(10L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refill-period");
        assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(10L, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refill-period");
        assertThatThrownBy(() -> new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refill-period");
    }
}
