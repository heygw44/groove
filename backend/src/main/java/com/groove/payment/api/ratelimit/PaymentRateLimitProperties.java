package com.groove.payment.api.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 결제 엔드포인트 Rate Limit 정책 설정. groove.payment.rate-limit.* 키와 매핑된다.
 *
 * <p>post: 결제 생성(generic 요청 + 토스 checkout) 회원 키잉 한도({@link PaymentRateLimitPolicy}).
 * webhook: 토스 웹훅 IP 키잉 한도({@link PaymentWebhookRateLimitPolicy}, #320).
 */
@ConfigurationProperties(prefix = "groove.payment.rate-limit")
public record PaymentRateLimitProperties(Policy post, Policy webhook) {

    public PaymentRateLimitProperties {
        if (post == null) {
            throw new IllegalStateException("groove.payment.rate-limit.post 설정이 필요합니다");
        }
        if (webhook == null) {
            throw new IllegalStateException("groove.payment.rate-limit.webhook 설정이 필요합니다");
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
