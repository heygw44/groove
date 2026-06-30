package com.groove.auth.security.ratelimit;

import com.groove.auth.security.JwtClaims;
import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRole;
import com.groove.support.RateLimitTestBuckets;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordChangeRateLimitPolicyTest {

    private JwtProvider jwtProvider;
    private PasswordChangeRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        AuthRateLimitProperties properties = new AuthRateLimitProperties(
                new AuthRateLimitProperties.Policy(10L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(3L, Duration.ofMinutes(1)),
                new AuthRateLimitProperties.Policy(2L, Duration.ofMinutes(1))
        );
        policy = new PasswordChangeRateLimitPolicy(properties, jwtProvider);
    }

    @Test
    void hasFixedName() {
        assertThat(policy.name()).isEqualTo("auth-password-change");
    }

    @Test
    void keyResolver_validToken_memberKey() {
        when(jwtProvider.parseAccessToken("good-token")).thenReturn(new JwtClaims(42L, MemberRole.USER));
        MockHttpServletRequest request = request("PATCH", "/api/v1/members/me/password");
        request.addHeader("Authorization", "Bearer good-token");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("member:42");
    }

    @Test
    void keyResolver_noToken_ipFallback() {
        MockHttpServletRequest request = request("PATCH", "/api/v1/members/me/password");
        request.setRemoteAddr("198.51.100.42");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("ip:198.51.100.42");
    }

    @Test
    void keyResolver_invalidToken_ipFallback() {
        when(jwtProvider.parseAccessToken("bad-token"))
                .thenThrow(new AuthException(ErrorCode.AUTH_INVALID_TOKEN));
        MockHttpServletRequest request = request("PATCH", "/api/v1/members/me/password");
        request.addHeader("Authorization", "Bearer bad-token");
        request.setRemoteAddr("198.51.100.9");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("ip:198.51.100.9");
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
        Bucket bucket = RateLimitTestBuckets.from(policy.bucketFactory().get());
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
