package com.groove.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Discogs 재검증 스케줄러 설정. 신선도 TTL(stale 판정 기준)은 {@code groove.catalog.freshness.ttl} 을
 * 그대로 참조하므로 여기에는 담지 않는다. failureCooldown 은 실패한 행을 후보에서 빼 두는 시간으로,
 * TTL 보다 충분히 짧아야 일시 장애가 지나간 뒤 TTL 안에 다시 시도된다.
 */
@ConfigurationProperties(prefix = "groove.catalog.resync")
public record CatalogResyncProperties(
	@DefaultValue("5m") Duration interval,
	@DefaultValue("150") int maxCallsPerRun,
	@DefaultValue("0 15 2 * * *") String sweepCron,
	@DefaultValue("7d") Duration viewWindow,
	@DefaultValue("1h") Duration failureCooldown
) {
}
