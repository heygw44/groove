package com.groove.common.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 멱등성 인프라 구성. IdempotencyProperties 바인딩을 활성화한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyConfig {
}
