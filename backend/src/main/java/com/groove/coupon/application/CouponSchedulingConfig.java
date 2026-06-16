package com.groove.coupon.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 쿠폰 도메인 스케줄링 인프라.
 *
 * <p>CouponExpirationProperties 바인딩을 활성화한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CouponExpirationProperties.class)
public class CouponSchedulingConfig {
}
