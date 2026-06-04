package com.groove.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 멱등성 인프라 구성.
 *
 * <p>{@link IdempotencyProperties} 바인딩을 활성화한다. {@code REQUIRES_NEW} 트랜잭션 템플릿은
 * 공용 빈({@code com.groove.common.transaction.CommonTransactionConfig#REQUIRES_NEW_TX_TEMPLATE})
 * 으로 끌어올려, 정리 태스크/멱등 서비스/만료 배치가 동일 빈을 공유한다 (#92 리뷰).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {
}
