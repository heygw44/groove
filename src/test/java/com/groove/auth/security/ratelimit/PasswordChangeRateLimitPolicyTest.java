package com.groove.auth.security.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordChangeRateLimitPolicyTest {

    private PasswordChangeRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
                new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(3L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(2L, Duration.ofMinutes(1))
        );
        policy = new PasswordChangeRateLimitPolicy(properties);
    }

    @Test
    void hasFixedNameAndClientIpKeyResolver() {
        assertThat(policy.name()).isEqualTo("auth-password-change");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");
        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("198.51.100.42");
    }

    @Test
    void appliesOnlyToPatchPasswordPath() {
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me/password"))).isTrue();
        assertThat(policy.appliesTo(request("GET", "/api/v1/members/me/password"))).isFalse();
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/members/me/password"))).isFalse();
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me/password/extra"))).isFalse();
    }

    @Test
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me//password"))).isTrue();
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me/./password"))).isTrue();
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me/foo/../password"))).isTrue();
        assertThat(policy.appliesTo(request("PATCH", "/api/v1/members/me/password;jsessionid=abc"))).isTrue();
    }

    @Test
    void bucketFactoryProducesBucketWithConfiguredCapacity() {
        Bucket bucket = policy.bucketFactory().get();
        assertThat(bucket.getAvailableTokens()).isEqualTo(2L);
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
