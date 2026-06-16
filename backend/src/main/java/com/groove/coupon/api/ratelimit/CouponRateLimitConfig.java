package com.groove.coupon.api.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 쿠폰 Rate Limit 설정 바인딩 — CouponRateLimitProperties 를 활성화한다.
 */
@Configuration
@EnableConfigurationProperties(CouponRateLimitProperties.class)
public class CouponRateLimitConfig {
}
