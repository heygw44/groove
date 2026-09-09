package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.catalog.config.CatalogFreshnessProperties;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Product;

class CatalogFreshnessTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
	private static final Duration TTL = Duration.ofHours(6);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 12, 0, 0);

	private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
	private final Artist artist = ArtistFixture.create();
	private final Album album = AlbumFixture.create(artist, "Kind of Blue");

	@Nested
	@DisplayName("isStale()")
	class IsStale {

		@Test
		@DisplayName("discogs_release_id 가 없는 자체 입력 상품은 동기화 시각이 없어도 stale 이 아니다")
		void neverStaleWithoutDiscogsReleaseId() {
			// given
			Product product = ProductFixture.create(artist);
			CatalogFreshness freshness = freshnessWith(true);

			// when & then
			assertThat(freshness.isStale(product)).isFalse();
		}

		@Test
		@DisplayName("discogs_synced_at 이 NULL 인 Discogs 상품은 stale 이다")
		void staleWhenSyncedAtIsNull() {
			// given
			Product product = discogsProduct(null);
			CatalogFreshness freshness = freshnessWith(true);

			// when & then
			assertThat(freshness.isStale(product)).isTrue();
		}

		@Test
		@DisplayName("동기화 시각이 TTL 을 넘기면 stale 이다")
		void staleWhenSyncedBeforeTtl() {
			// given
			Product product = discogsProduct(NOW.minus(TTL).minusSeconds(1));
			CatalogFreshness freshness = freshnessWith(true);

			// when & then
			assertThat(freshness.isStale(product)).isTrue();
		}

		@Test
		@DisplayName("동기화 시각이 정확히 TTL 경계면 stale 이 아니다")
		void freshAtExactTtlBoundary() {
			// given
			Product product = discogsProduct(NOW.minus(TTL));
			CatalogFreshness freshness = freshnessWith(true);

			// when & then
			assertThat(freshness.isStale(product)).isFalse();
		}

		@Test
		@DisplayName("TTL 이내면 stale 이 아니다")
		void freshWithinTtl() {
			// given
			Product product = discogsProduct(NOW.minus(TTL).plusMinutes(1));
			CatalogFreshness freshness = freshnessWith(true);

			// when & then
			assertThat(freshness.isStale(product)).isFalse();
		}

		@Test
		@DisplayName("킬 스위치가 꺼져 있으면 TTL 을 한참 넘긴 상품도 stale 이 아니다")
		void neverStaleWhenDisabled() {
			// given
			Product product = discogsProduct(NOW.minusDays(30));
			CatalogFreshness freshness = freshnessWith(false);

			// when & then
			assertThat(freshness.isStale(product)).isFalse();
		}
	}

	private CatalogFreshness freshnessWith(boolean enabled) {
		return new CatalogFreshness(new CatalogFreshnessProperties(TTL, enabled), clock);
	}

	private Product discogsProduct(LocalDateTime discogsSyncedAt) {
		return Product.createImported(album, "Kind of Blue", artist, null, "US", 1959, "CS 8163", "888880123456",
				EditionType.STANDARD, new BigDecimal("45000"), 123L, discogsSyncedAt);
	}
}
