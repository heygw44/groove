package com.groove.order.api.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 주문 Rate Limit 설정 바인딩 — {@link OrderRateLimitProperties} 를 활성화한다.
 *
 * <p>정책 빈({@link OrderGuestLookupRateLimitPolicy})은 {@code @Component} 로 자동 등록되며,
 * {@code RateLimitRegistry} 가 {@code List<RateLimitPolicy>} 주입으로 수집한다.
 */
@Configuration
@EnableConfigurationProperties(OrderRateLimitProperties.class)
public class OrderRateLimitConfig {
}
