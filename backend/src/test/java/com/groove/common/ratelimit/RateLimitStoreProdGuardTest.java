package com.groove.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitStoreProdGuardTest {

    @Test
    void rejectsNonRedisStoreInProd() {
        MockEnvironment environment = new MockEnvironment().withProperty("groove.rate-limit.store", "caffeine");

        assertThatThrownBy(() -> new RateLimitStoreConfig.RateLimitStoreProdGuard(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis");
    }

    @Test
    void rejectsMissingStoreInProd() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new RateLimitStoreConfig.RateLimitStoreProdGuard(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsRedisStoreInProd() {
        MockEnvironment environment = new MockEnvironment().withProperty("groove.rate-limit.store", "redis");

        assertThatCode(() -> new RateLimitStoreConfig.RateLimitStoreProdGuard(environment))
                .doesNotThrowAnyException();
    }
}
