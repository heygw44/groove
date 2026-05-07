package com.groove.auth.security.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimitPolicyTest {

    private LoginRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
                new AuthRateLimitProperties.Policy(2L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(3L, Duration.ofMinutes(1))
        );
        policy = new LoginRateLimitPolicy(properties);
    }

    @Test
    void hasFixedNameAndClientIpKeyResolver() {
        assertThat(policy.name()).isEqualTo("auth-login");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.1");
        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void appliesOnlyToPostLoginPath() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/login"))).isTrue();
        assertThat(policy.appliesTo(request("GET", "/api/v1/auth/login"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/signup"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/refresh"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/login/extra"))).isFalse();
    }

    @Test
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth//login"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/./login"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/foo/../login"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/login;jsessionid=abc"))).isTrue();
    }

    @Test
    void bucketFactoryProducesIndependentBucketsWithConfiguredCapacity() {
        Bucket first = policy.bucketFactory().get();
        Bucket second = policy.bucketFactory().get();

        ConsumptionProbe firstProbe = first.tryConsumeAndReturnRemaining(1);
        assertThat(firstProbe.isConsumed()).isTrue();
        assertThat(firstProbe.getRemainingTokens()).isEqualTo(1L);

        assertThat(first.tryConsume(1)).isTrue();
        assertThat(first.tryConsume(1)).isFalse();

        // 새로 생성된 버킷은 독립적
        assertThat(second.getAvailableTokens()).isEqualTo(2L);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
