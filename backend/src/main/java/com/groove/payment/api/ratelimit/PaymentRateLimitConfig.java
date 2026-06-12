package com.groove.payment.api.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 결제 Rate Limit 설정 바인딩 — {@link PaymentRateLimitProperties} 를 활성화한다.
 *
 * <p>정책 빈({@link PaymentRateLimitPolicy})은 {@code @Component} 로 자동 등록되며,
 * {@code RateLimitRegistry} 가 {@code List<RateLimitPolicy>} 주입으로 수집한다.
 */
@Configuration
@EnableConfigurationProperties(PaymentRateLimitProperties.class)
public class PaymentRateLimitConfig {
}
