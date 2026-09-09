package com.groove.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Discogs 유래 프레싱 스펙의 신선도 판정 기준. TTL 은 Discogs 약관의 6시간 규칙이 근거다. */
@ConfigurationProperties(prefix = "groove.catalog.freshness")
public record CatalogFreshnessProperties(
		@DefaultValue("6h") Duration ttl,
		@DefaultValue("true") boolean enabled) {
}
