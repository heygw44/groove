package com.groove.order.api.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 주문 Rate Limit 설정 바인딩 — OrderRateLimitProperties 를 활성화한다. */
@Configuration
@EnableConfigurationProperties(OrderRateLimitProperties.class)
public class OrderRateLimitConfig {
}
