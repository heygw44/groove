package com.groove.payment.api.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 결제 Rate Limit 설정 바인딩 — PaymentRateLimitProperties 를 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(PaymentRateLimitProperties.class)
public class PaymentRateLimitConfig {
}
