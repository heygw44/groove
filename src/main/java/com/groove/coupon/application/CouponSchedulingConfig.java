package com.groove.coupon.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 쿠폰 도메인 스케줄링 인프라 (이슈 #92).
 *
 * <p>{@link CouponExpirationProperties} 바인딩을 활성화한다. {@code REQUIRES_NEW} 트랜잭션 템플릿은
 * 공용 빈({@code com.groove.common.transaction.CommonTransactionConfig#REQUIRES_NEW_TX_TEMPLATE})
 * 으로 공유한다 (#92 리뷰).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CouponExpirationProperties.class)
public class CouponSchedulingConfig {
}
