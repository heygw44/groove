package com.groove.order.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 주문 엔드포인트 Rate Limit 정책 설정 — groove.order.rate-limit.* 키와 매핑된다. */
@ConfigurationProperties(prefix = "groove.order.rate-limit")
public record OrderRateLimitProperties(Policy guestLookup) {

    public OrderRateLimitProperties {
        if (guestLookup == null) {
            throw new IllegalStateException("groove.order.rate-limit.guest-lookup 설정이 필요합니다");
        }
    }

    public record Policy(long capacity, Duration refillPeriod) {

        public Policy {
            if (capacity <= 0) {
                throw new IllegalStateException("rate-limit capacity 는 양수여야 합니다 (현재: " + capacity + ")");
            }
            if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
                throw new IllegalStateException("rate-limit refill-period 는 양수 Duration 이어야 합니다");
            }
        }
    }
}
