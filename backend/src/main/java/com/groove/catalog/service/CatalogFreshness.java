package com.groove.catalog.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.groove.catalog.config.CatalogFreshnessProperties;
import com.groove.product.entity.Product;

import lombok.RequiredArgsConstructor;

/** Discogs 유래 프레싱 스펙이 재검증 TTL 을 넘겼는지 판정한다. 규칙은 여기 한 곳에만 둔다. */
@Component
@RequiredArgsConstructor
public class CatalogFreshness {

	private final CatalogFreshnessProperties properties;
	private final Clock clock;

	public boolean isStale(Product product) {
		if (!properties.enabled()) {
			return false;
		}
		if (product.getDiscogsReleaseId() == null) {
			return false;
		}
		LocalDateTime syncedAt = product.getDiscogsSyncedAt();
		if (syncedAt == null) {
			return true;
		}
		LocalDateTime deadline = LocalDateTime.now(clock).minus(properties.ttl());
		return syncedAt.isBefore(deadline);
	}
}
