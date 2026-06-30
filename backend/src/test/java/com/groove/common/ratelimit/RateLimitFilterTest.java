package com.groove.common.ratelimit;

import com.groove.support.RateLimitTestBuckets;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesThroughWhenNoPolicyRegistered() throws Exception {
        RateLimitRegistry registry = new RateLimitRegistry(List.of(), RateLimitTestBuckets.newProxyManager());
        RateLimitFilter filter = new RateLimitFilter(registry, objectMapper);
        AtomicInteger called = new AtomicInteger();
        FilterChain chain = (req, res) -> called.incrementAndGet();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(called.get()).isEqualTo(1);
    }

    @Test
    void allowsRequestsWithinBucketCapacityAndAddsRemainingHeader() throws Exception {
        RateLimitFilter filter = filterWith(fixedPolicy("ip", 2));
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
        RateLimitFilter filter = filterWith(fixedPolicy("ip", 1));
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
        RateLimitFilter filter = filterWith(fixedPolicy("ip", 1));
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
        RateLimitFilter filter = filterWith(fixedPolicy("ip", 1));
        FilterChain chain = (req, res) -> {
        };

        MockHttpServletResponse a = new MockHttpServletResponse();
        MockHttpServletResponse b = new MockHttpServletResponse();
        filter.doFilter(requestFrom("3.3.3.3"), a, chain);
        filter.doFilter(requestFrom("4.4.4.4"), b, chain);

        assertThat(a.getStatus()).isEqualTo(200);
        assertThat(b.getStatus()).isEqualTo(200);
    }

    @Test
    void failOpenPolicyPassesThroughWhenStoreFails() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitRegistry(List.of(fixedPolicy("ip", 1, true)),
                        throwingProxyManager(new RedisException("redis down"))), objectMapper);
        AtomicInteger downstream = new AtomicInteger();
        FilterChain chain = (req, res) -> downstream.incrementAndGet();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFrom("5.5.5.5"), response, chain);

        assertThat(downstream.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void failClosedPolicyReturnsTooManyRequestsWhenStoreFails() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitRegistry(List.of(fixedPolicy("ip", 1, false)),
                        throwingProxyManager(new RedisException("redis down"))), objectMapper);
        AtomicInteger downstream = new AtomicInteger();
        FilterChain chain = (req, res) -> downstream.incrementAndGet();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestFrom("6.6.6.6"), response, chain);

        assertThat(downstream.get()).isZero();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/problem+json");
    }

    @Test
    void nonStoreRuntimeExceptionPropagatesInsteadOfFailingOpen() {
        RateLimitFilter filter = new RateLimitFilter(
                new RateLimitRegistry(List.of(fixedPolicy("ip", 1, true)),
                        throwingProxyManager(new IllegalStateException("bug, not a store failure"))), objectMapper);
        FilterChain chain = (req, res) -> {
        };

        // fail-open 정책이라도 store 장애가 아닌 RuntimeException 은 삼키지 않고 그대로 전파한다.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> filter.doFilter(requestFrom("7.7.7.7"), new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);
    }

    private RateLimitFilter filterWith(RateLimitPolicy policy) {
        return new RateLimitFilter(
                new RateLimitRegistry(List.of(policy), RateLimitTestBuckets.newProxyManager()), objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static ProxyManager<String> throwingProxyManager(RuntimeException failure) {
        ProxyManager<String> proxyManager = mock(ProxyManager.class);
        BucketProxy throwing = mock(BucketProxy.class);
        when(proxyManager.getProxy(anyString(), any())).thenReturn(throwing);
        when(throwing.tryConsumeAndReturnRemaining(anyLong())).thenThrow(failure);
        return proxyManager;
    }

    private MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private RateLimitPolicy fixedPolicy(String name, long capacity) {
        return fixedPolicy(name, capacity, true);
    }

    private RateLimitPolicy fixedPolicy(String name, long capacity, boolean failOpen) {
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
            public Supplier<BucketConfiguration> bucketFactory() {
                return () -> BucketConfiguration.builder()
                        .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, Duration.ofMinutes(1)))
                        .build();
            }

            @Override
            public RateLimitKeyResolver keyResolver() {
                return RateLimitKeyResolver.clientIp();
            }

            @Override
            public boolean failOpen() {
                return failOpen;
            }
        };
    }
}
