package com.groove.coupon.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 쿠폰 도메인 설정 바인딩 — CouponExpirationProperties(만료 배치)·CouponRestoreProperties(복원 유예기간). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CouponExpirationProperties.class, CouponRestoreProperties.class})
public class CouponSchedulingConfig {
}
