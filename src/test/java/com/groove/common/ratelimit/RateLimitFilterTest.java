package com.groove.common.ratelimit;

import io.github.bucket4j.Bucket;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesThroughWhenNoPolicyRegistered() throws Exception {
        RateLimitRegistry registry = new RateLimitRegistry(List.of());
        RateLimitFilter filter = new RateLimitFilter(registry, objectMapper);
        AtomicInteger called = new AtomicInteger();
        FilterChain chain = (req, res) -> called.incrementAndGet();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(called.get()).isEqualTo(1);
    }

    @Test
    void allowsRequestsWithinBucketCapacityAndAddsRemainingHeader() throws Exception {
        RateLimitPolicy policy = fixedPolicy("ip", 2);
        RateLimitFilter filter = new RateLimitFilter(new RateLimitRegistry(List.of(policy)), objectMapper);
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(requestFrom("1.1.1.1"), first, chain);

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(requestFrom("1.1.1.1"), second, chain);

        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(first.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("1");
        assertThat(second.getHeader(RateLimitFilter.HEADER_REMAINING)).isEqualTo("0");
    }

    @Test
    void returnsTooManyRequestsWhenExceeded() throws Exception {
        RateLimitPolicy policy = fixedPolicy("ip", 1);
        RateLimitFilter filter = new RateLimitFilter(new RateLimitRegistry(List.of(policy)), objectMapper);
        AtomicInteger downstream = new AtomicInteger();
        FilterChain chain = (req, res) -> downstream.incrementAndGet();

        filter.doFilter(requestFrom("2.2.2.2"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(requestFrom("2.2.2.2"), blocked, chain);

        assertThat(downstream.get()).isEqualTo(1);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentType()).contains("application/problem+json");
        assertThat(blocked.getHeader(RateLimitFilter.HEADER_RETRY_AFTER_SECONDS)).isNotNull();
        String body = blocked.getContentAsString();
        assertThat(body).contains("SYSTEM_002");
        assertThat(body).contains("\"timestamp\"");
        assertThat(body).contains("\"traceId\"");
        assertThat(body).contains("\"retryAfterSeconds\"");
    }

    @Test
    void ignoresXForwardedForHeaderForKey() throws Exception {
        RateLimitPolicy policy = fixedPolicy("ip", 1);
        RateLimitFilter filter = new RateLimitFilter(new RateLimitRegistry(List.of(policy)), objectMapper);
        FilterChain chain = (req, res) -> {};

        MockHttpServletRequest first = requestFrom("9.9.9.9");
        first.addHeader("X-Forwarded-For", "1.2.3.4");
        MockHttpServletResponse firstResp = new MockHttpServletResponse();
        filter.doFilter(first, firstResp, chain);

        MockHttpServletRequest second = requestFrom("9.9.9.9");
        second.addHeader("X-Forwarded-For", "5.6.7.8");
        MockHttpServletResponse secondResp = new MockHttpServletResponse();
        filter.doFilter(second, secondResp, chain);

        assertThat(firstResp.getStatus()).isEqualTo(200);
        assertThat(secondResp.getStatus()).isEqualTo(429);
    }

    @Test
    void appliesSeparateBucketPerKey() throws Exception {
        RateLimitPolicy policy = fixedPolicy("ip", 1);
        RateLimitFilter filter = new RateLimitFilter(new RateLimitRegistry(List.of(policy)), objectMapper);
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletResponse a = new MockHttpServletResponse();
        MockHttpServletResponse b = new MockHttpServletResponse();
        filter.doFilter(requestFrom("3.3.3.3"), a, chain);
        filter.doFilter(requestFrom("4.4.4.4"), b, chain);

        assertThat(a.getStatus()).isEqualTo(200);
        assertThat(b.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private RateLimitPolicy fixedPolicy(String name, long capacity) {
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
                        .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)))
                        .build();
            }

            @Override
            public RateLimitKeyResolver keyResolver() {
                return RateLimitKeyResolver.clientIp();
            }
        };
    }
}
