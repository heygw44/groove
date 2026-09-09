package com.groove.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Discogs 재검증 스케줄러 설정. 신선도 TTL(stale 판정 기준)은 {@code groove.catalog.freshness.ttl} 을
 * 그대로 참조하므로 여기에는 담지 않는다.
 */
@ConfigurationProperties(prefix = "groove.catalog.resync")
public record CatalogResyncProperties(
	@DefaultValue("5m") Duration interval,
	@DefaultValue("150") int maxCallsPerRun,
	@DefaultValue("0 15 2 * * *") String sweepCron,
	@DefaultValue("7d") Duration viewWindow
) {
}
