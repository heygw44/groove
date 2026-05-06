package com.groove.common.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitRegistryTest {

    @Test
    void rejectsDuplicatePolicyNames() {
        RateLimitPolicy a = simplePolicy("dup");
        RateLimitPolicy b = simplePolicy("dup");

        assertThatThrownBy(() -> new RateLimitRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void higherOrderPolicyWinsOverLowerOrderWhenBothMatch() {
        RateLimitPolicy lowPriority = orderedPolicy("ip", Ordered.LOWEST_PRECEDENCE);
        RateLimitPolicy highPriority = orderedPolicy("user", Ordered.HIGHEST_PRECEDENCE);

        RateLimitRegistry registry = new RateLimitRegistry(List.of(lowPriority, highPriority));

        Optional<RateLimitRegistry.MatchedBucket> matched = registry.match(new MockHttpServletRequest());

        assertThat(matched).isPresent();
        assertThat(matched.get().policy().name()).isEqualTo("user");
    }

    @Test
    void returnsEmptyWhenNoPolicyMatches() {
        RateLimitPolicy nonMatching = new RateLimitPolicy() {
            @Override
            public String name() {
                return "none";
            }

            @Override
            public boolean appliesTo(HttpServletRequest request) {
                return false;
            }

            @Override
            public Supplier<Bucket> bucketFactory() {
                return () -> Bucket.builder()
                        .addLimit(l -> l.capacity(1).refillGreedy(1, Duration.ofMinutes(1)))
                        .build();
            }

            @Override
            public RateLimitKeyResolver keyResolver() {
                return RateLimitKeyResolver.clientIp();
            }
        };

        RateLimitRegistry registry = new RateLimitRegistry(List.of(nonMatching));

        assertThat(registry.match(new MockHttpServletRequest())).isEmpty();
    }

    private RateLimitPolicy simplePolicy(String name) {
        return new RateLimitPolicy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean appliesTo(HttpServletRequest request) {
                return true;
            }

            @Override
            public Supplier<Bucket> bucketFactory() {
                return () -> Bucket.builder()
                        .addLimit(l -> l.capacity(1).refillGreedy(1, Duration.ofMinutes(1)))
                        .build();
            }

            @Override
            public RateLimitKeyResolver keyResolver() {
                return RateLimitKeyResolver.clientIp();
            }
        };
    }

    private RateLimitPolicy orderedPolicy(String name, int order) {
        if (order == Ordered.HIGHEST_PRECEDENCE) {
            return new HighestOrderPolicy(name);
        }
        return new LowestOrderPolicy(name);
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    static final class HighestOrderPolicy implements RateLimitPolicy {
        private final String name;

        HighestOrderPolicy(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean appliesTo(HttpServletRequest request) {
            return true;
        }

        @Override
        public Supplier<Bucket> bucketFactory() {
            return () -> Bucket.builder()
                    .addLimit(l -> l.capacity(1).refillGreedy(1, Duration.ofMinutes(1)))
                    .build();
        }

        @Override
        public RateLimitKeyResolver keyResolver() {
            return RateLimitKeyResolver.clientIp();
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    static final class LowestOrderPolicy implements RateLimitPolicy {
        private final String name;

        LowestOrderPolicy(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean appliesTo(HttpServletRequest request) {
            return true;
        }

        @Override
        public Supplier<Bucket> bucketFactory() {
            return () -> Bucket.builder()
                    .addLimit(l -> l.capacity(1).refillGreedy(1, Duration.ofMinutes(1)))
                    .build();
        }

        @Override
        public RateLimitKeyResolver keyResolver() {
            return RateLimitKeyResolver.clientIp();
        }
    }
}
