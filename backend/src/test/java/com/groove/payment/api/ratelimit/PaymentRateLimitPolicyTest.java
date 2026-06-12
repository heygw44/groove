package com.groove.payment.api.ratelimit;

import com.groove.auth.security.JwtClaims;
import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRole;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PaymentRateLimitPolicy — 경로 매칭 · 회원 키잉 · 버킷")
class PaymentRateLimitPolicyTest {

    private JwtProvider jwtProvider;
    private PaymentRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtProvider.class);
        PaymentRateLimitProperties properties = new PaymentRateLimitProperties(
                new PaymentRateLimitProperties.Policy(2L, Duration.ofMinutes(1)));
        policy = new PaymentRateLimitPolicy(properties, jwtProvider);
    }

    @Test
    @DisplayName("고정 이름 payment-create")
    void hasFixedName() {
        assertThat(policy.name()).isEqualTo("payment-create");
    }

    @Test
    @DisplayName("POST /payments 에만 적용 (메서드·고정경로 매칭)")
    void appliesOnlyToPaymentPost() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments"))).isTrue();
        assertThat(policy.appliesTo(request("GET", "/api/v1/payments"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/123"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payment"))).isFalse();
    }

    @Test
    @DisplayName("경로 정규화 후 매칭 (// · /./ · 매트릭스 파라미터)")
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("POST", "/api/v1//payments"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/./payments"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments;jsessionid=abc"))).isTrue();
    }

    @Test
    @DisplayName("키잉 — 유효 Bearer 토큰이면 member:{id}")
    void keyResolver_validToken_memberKey() {
        when(jwtProvider.parseAccessToken("good-token")).thenReturn(new JwtClaims(42L, MemberRole.USER));
        MockHttpServletRequest request = request("POST", "/api/v1/payments");
        request.addHeader("Authorization", "Bearer good-token");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("member:42");
    }

    @Test
    @DisplayName("키잉 — 토큰 없으면(게스트) IP 폴백")
    void keyResolver_noToken_ipFallback() {
        MockHttpServletRequest request = request("POST", "/api/v1/payments");
        request.setRemoteAddr("198.51.100.7");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("ip:198.51.100.7");
    }

    @Test
    @DisplayName("키잉 — 위조/만료 토큰이면 IP 폴백")
    void keyResolver_invalidToken_ipFallback() {
        when(jwtProvider.parseAccessToken("bad-token"))
                .thenThrow(new AuthException(ErrorCode.AUTH_INVALID_TOKEN));
        MockHttpServletRequest request = request("POST", "/api/v1/payments");
        request.addHeader("Authorization", "Bearer bad-token");
        request.setRemoteAddr("198.51.100.9");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("ip:198.51.100.9");
    }

    @Test
    @DisplayName("버킷 용량은 설정값(2)")
    void bucketFactoryUsesConfiguredCapacity() {
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
