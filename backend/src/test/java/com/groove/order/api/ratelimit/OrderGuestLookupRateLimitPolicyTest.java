package com.groove.order.api.ratelimit;

import com.groove.support.RateLimitTestBuckets;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderGuestLookupRateLimitPolicy — 경로 매칭 · IP 키잉 · 버킷")
class OrderGuestLookupRateLimitPolicyTest {

    private static final String SAMPLE = "/api/v1/orders/ORD-20260101-AB12CD/guest-lookup";

    private OrderGuestLookupRateLimitPolicy policy;

    @BeforeEach
    void setUp() {
        OrderRateLimitProperties properties = new OrderRateLimitProperties(
                new OrderRateLimitProperties.Policy(2L, Duration.ofMinutes(1)));
        policy = new OrderGuestLookupRateLimitPolicy(properties);
    }

    @Test
    @DisplayName("고정 이름 order-guest-lookup")
    void hasFixedName() {
        assertThat(policy.name()).isEqualTo("order-guest-lookup");
    }

    @Test
    @DisplayName("POST /orders/{no}/guest-lookup 에만 적용 (메서드·경로 매칭)")
    void appliesOnlyToGuestLookupPost() {
        assertThat(policy.appliesTo(request("POST", SAMPLE))).isTrue();
        assertThat(policy.appliesTo(request("GET", SAMPLE))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/orders/ORD-20260101-AB12CD/cancel"))).isFalse();
        assertThat(policy.appliesTo(request("POST", "/api/v1/orders"))).isFalse();
        assertThat(policy.appliesTo(request("POST", SAMPLE + "/extra"))).isFalse();
    }

    @Test
    @DisplayName("경로 정규화 후 매칭 (// · /./ · 매트릭스 파라미터)")
    void appliesAfterPathNormalization() {
        assertThat(policy.appliesTo(request("POST", "/api/v1//orders/ORD-20260101-AB12CD/guest-lookup"))).isTrue();
        assertThat(policy.appliesTo(request("POST", "/api/v1/orders/ORD-20260101-AB12CD/./guest-lookup"))).isTrue();
        assertThat(policy.appliesTo(request("POST", SAMPLE + ";jsessionid=abc"))).isTrue();
    }

    @Test
    @DisplayName("키잉 — 클라이언트 IP")
    void keyResolver_clientIp() {
        MockHttpServletRequest request = request("POST", SAMPLE);
        request.setRemoteAddr("203.0.113.7");

        assertThat(policy.keyResolver().resolveKey(request)).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("버킷 용량은 설정값(2)")
    void bucketFactoryUsesConfiguredCapacity() {
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
