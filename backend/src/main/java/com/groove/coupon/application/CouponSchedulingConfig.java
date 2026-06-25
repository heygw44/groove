package com.groove.coupon.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 쿠폰 도메인 설정 바인딩.
 *
 * <p>CouponExpirationProperties(만료 배치)·CouponRestoreProperties(복원 유예기간) 바인딩을 활성화한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CouponExpirationProperties.class, CouponRestoreProperties.class})
public class CouponSchedulingConfig {
}
