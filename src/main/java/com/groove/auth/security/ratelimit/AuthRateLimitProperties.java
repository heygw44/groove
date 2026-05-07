package com.groove.auth.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 인증 엔드포인트 Rate Limit 정책 설정.
 *
 * <p>{@code application.yaml} 의 {@code groove.auth.rate-limit.*} 키와 매핑된다.
 * 정책 수치는 운영 측정 결과에 따라 환경별 yaml 또는 환경 변수에서 override 한다.
 */
@ConfigurationProperties(prefix = "groove.auth.rate-limit")
public record AuthRateLimitProperties(Policy login, Policy signup) {

    public AuthRateLimitProperties {
        if (login == null) {
            throw new IllegalStateException("groove.auth.rate-limit.login 설정이 필요합니다");
        }
        if (signup == null) {
            throw new IllegalStateException("groove.auth.rate-limit.signup 설정이 필요합니다");
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
