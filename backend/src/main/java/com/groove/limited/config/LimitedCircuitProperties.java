package com.groove.limited.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 한정반 Redis 장애 서킷·DB 폴백 설정. */
@ConfigurationProperties(prefix = "groove.limited.circuit")
public record LimitedCircuitProperties(
	@DefaultValue("5") int failureThreshold,
	@DefaultValue("10s") Duration openDuration,
	@DefaultValue("5") int fallbackPermits,
	@DefaultValue("true") boolean fallbackEnabled
) {
}
