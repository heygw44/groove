package com.groove.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 카탈로그 적재 배치 잡(청크·재시도·백오프) 설정. */
@ConfigurationProperties(prefix = "catalog.import")
public record CatalogImportProperties(
		@DefaultValue("5") int chunkSize,
		@DefaultValue("50") int skipLimit,
		@DefaultValue("3") int retryLimit,
		@DefaultValue("2s") Duration backoffInitial,
		@DefaultValue("8s") Duration backoffMax) {
}
