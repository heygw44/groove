package com.groove.payment.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 결제 엔드포인트 Rate Limit 정책 설정 (#208).
 *
 * <p>{@code application.yaml} 의 {@code groove.payment.rate-limit.*} 키와 매핑된다.
 * 정책 수치는 운영 측정 결과에 따라 환경별 yaml 또는 환경 변수에서 override 한다.
 */
@ConfigurationProperties(prefix = "groove.payment.rate-limit")
public record PaymentRateLimitProperties(Policy post) {

    public PaymentRateLimitProperties {
        if (post == null) {
            throw new IllegalStateException("groove.payment.rate-limit.post 설정이 필요합니다");
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
