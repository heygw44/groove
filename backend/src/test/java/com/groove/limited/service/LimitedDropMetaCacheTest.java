package com.groove.limited.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.config.LimitedProperties;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class LimitedDropMetaCacheTest {

	private static final Long DROP_ID = 10L;

	@Mock
	private LimitedDropRepository limitedDropRepository;

	private MutableClock clock;

	private LimitedDropMetaCache cache(Duration ttl) {
		clock = new MutableClock(Instant.parse("2026-09-16T00:00:00Z"), ZoneOffset.UTC);
		return new LimitedDropMetaCache(limitedDropRepository, new LimitedProperties(true, ttl), clock);
	}

	private LimitedDrop drop() {
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
		return LimitedDropFixture.withId(LimitedDropFixture.open(product, 10), DROP_ID);
	}

	@Nested
	@DisplayName("get()")
	class Get {

		@Test
		@DisplayName("연속으로 여러 번 호출해도 findById() 는 한 번만 호출한다")
		void loadsOnceForRepeatedCalls() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ofSeconds(3));
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.of(drop()));

			// when
			for (int i = 0; i < 5; i++) {
				cache.get(DROP_ID);
			}

			// then
			verify(limitedDropRepository, times(1)).findById(DROP_ID);
			assertThat(cache.loadCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("TTL 이 지나면 다시 적재한다")
		void reloadsAfterTtlExpires() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ofSeconds(3));
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.of(drop()));
			cache.get(DROP_ID);

			// when
			clock.advance(Duration.ofSeconds(4));
			cache.get(DROP_ID);

			// then
			verify(limitedDropRepository, times(2)).findById(DROP_ID);
			assertThat(cache.loadCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("TTL 이 지나지 않으면 다시 적재하지 않는다")
		void doesNotReloadBeforeTtlExpires() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ofSeconds(3));
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.of(drop()));
			cache.get(DROP_ID);

			// when
			clock.advance(Duration.ofSeconds(2));
			cache.get(DROP_ID);

			// then
			verify(limitedDropRepository, times(1)).findById(DROP_ID);
		}

		@Test
		@DisplayName("TTL 이 0 이면 매번 다시 적재한다")
		void reloadsEveryTimeWhenTtlIsZero() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ZERO);
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.of(drop()));

			// when
			cache.get(DROP_ID);
			cache.get(DROP_ID);
			cache.get(DROP_ID);

			// then
			verify(limitedDropRepository, times(3)).findById(DROP_ID);
			assertThat(cache.loadCount()).isEqualTo(3);
		}

		@Test
		@DisplayName("없는 드롭은 empty 를 반환하고 캐시하지 않는다")
		void returnsEmptyAndDoesNotCacheWhenMissing() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ofSeconds(3));
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.empty());

			// when
			Optional<LimitedDropMeta> first = cache.get(DROP_ID);
			Optional<LimitedDropMeta> second = cache.get(DROP_ID);

			// then
			assertThat(first).isEmpty();
			assertThat(second).isEmpty();
			verify(limitedDropRepository, times(2)).findById(DROP_ID);
		}
	}

	@Nested
	@DisplayName("evict()")
	class Evict {

		@Test
		@DisplayName("지운 뒤 다시 조회하면 다시 적재한다")
		void reloadsAfterEvict() {
			// given
			LimitedDropMetaCache cache = cache(Duration.ofSeconds(3));
			given(limitedDropRepository.findById(DROP_ID)).willReturn(Optional.of(drop()));
			cache.get(DROP_ID);

			// when
			cache.evict(DROP_ID);
			cache.get(DROP_ID);

			// then
			verify(limitedDropRepository, times(2)).findById(DROP_ID);
			assertThat(cache.loadCount()).isEqualTo(2);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant instant;
		private final ZoneId zone;

		private MutableClock(Instant instant, ZoneId zone) {
			this.instant = instant;
			this.zone = zone;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
