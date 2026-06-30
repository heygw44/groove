package com.groove.payment.api.ratelimit;

import com.groove.support.RateLimitTestBuckets;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentWebhookRateLimitPolicy — 웹훅 경로 매칭 · IP 키잉 · 버킷")
class PaymentWebhookRateLimitPolicyTest {

    private PaymentWebhookRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        PaymentRateLimitProperties properties = new PaymentRateLimitProperties(
                new PaymentRateLimitProperties.Policy(5L, Duration.ofMinutes(1)),
                new PaymentRateLimitProperties.Policy(3L, Duration.ofMinutes(1)));
        policy = new PaymentWebhookRateLimitPolicy(properties);
    }

    @Test
    @DisplayName("고정 이름 payment-webhook")
    void hasFixedName() {
        assertThat(policy.name()).isEqualTo("payment-webhook");
    }

    @Test
    @DisplayName("POST /toss/webhook 에만 적용, 결제 생성·checkout 은 제외")
    void appliesOnlyToWebhookPost() {
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/toss/webhook"))).isTrue();
        assertThat(policy.appliesTo(request("GET", "/api/v1/payments/toss/webhook"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/toss/checkout"))).isFalse();
        // 목 웹훅은 별도 경로라 이 정책 대상이 아니다.
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/webhook"))).isFalse();
    }

    @Test
    @DisplayName("경로 정규화 후 매칭 (// · /./ · 매트릭스 파라미터)")
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("POST", "/api/v1//payments/toss/webhook"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/./toss/webhook"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/payments/toss/webhook;jsessionid=abc"))).isTrue();
    }

    @Test
    @DisplayName("키잉 — 클라이언트 IP")
    void keyResolver_clientIp() {
        MockHttpServletRequest request = request("POST", "/api/v1/payments/toss/webhook");
        request.setRemoteAddr("203.0.113.5");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("버킷 용량은 설정값(3)")
    void bucketFactoryUsesConfiguredCapacity() {
        Bucket bucket = RateLimitTestBuckets.from(policy.bucketFactory().get());
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
