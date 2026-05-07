package com.groove.auth.security.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SignupRateLimitPolicyTest {

    private SignupRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
                new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(3L, Duration.ofMinutes(1))
        );
        policy = new SignupRateLimitPolicy(properties);
    }

    @Test
    void hasFixedNameAndClientIpKeyResolver() {
        assertThat(policy.name()).isEqualTo("auth-signup");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void appliesOnlyToPostSignupPath() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/signup"))).isTrue();
        assertThat(policy.appliesTo(request("GET", "/api/v1/auth/signup"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/login"))).isFalse();
        assertThat(policy.appliesTo(request("PUT", "/api/v1/auth/signup"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/signup/confirm"))).isFalse();
    }

    @Test
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth//signup"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/./signup"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/foo/../signup"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/auth/signup;jsessionid=abc"))).isTrue();
    }

    @Test
    void bucketFactoryProducesBucketWithConfiguredSignupCapacity() {
        Bucket bucket = policy.bucketFactory().get();
        assertThat(bucket.getAvailableTokens()).isEqualTo(3L);
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(uri);
        return request;
    }
}
