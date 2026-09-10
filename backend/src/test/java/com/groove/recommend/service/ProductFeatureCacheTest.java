package com.groove.recommend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.product.entity.ProductStatus;
import com.groove.recommend.config.RecommendProperties;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.mapper.RecommendQueryMapper;

@ExtendWith(MockitoExtension.class)
class ProductFeatureCacheTest {

	private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 8, 0, 0);

	@Mock
	private RecommendQueryMapper recommendQueryMapper;

	private MutableClock clock;

	private ProductFeatureCache cache(Duration ttl) {
		clock = new MutableClock(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);
		return new ProductFeatureCache(recommendQueryMapper, new RecommendProperties(ttl, RecommendWeights.DEFAULT),
				clock);
	}

	private ProductFeatureRow row(Long id) {
		return new ProductFeatureRow(id, id, id, null, 2020, null, null, null, CREATED_AT, ProductStatus.ON_SALE,
				null);
	}

	@Nested
	@DisplayName("get()")
	class Get {

		@Test
		@DisplayName("연속으로 여러 번 호출해도 findProductFeatures() 는 한 번만 호출한다")
		void loadsOnceForRepeatedCalls() {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));

			// when
			for (int i = 0; i < 5; i++) {
				cache.get();
			}

			// then
			verify(recommendQueryMapper, times(1)).findProductFeatures();
			assertThat(cache.loadCount()).isEqualTo(1);
		}

		@Test
		@DisplayName("TTL 이 지나면 다시 적재한다")
		void reloadsAfterTtlExpires() {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));
			cache.get();

			// when
			clock.advance(Duration.ofSeconds(61));
			cache.get();

			// then
			verify(recommendQueryMapper, times(2)).findProductFeatures();
			assertThat(cache.loadCount()).isEqualTo(2);
		}

		@Test
		@DisplayName("TTL 이 지나지 않으면 다시 적재하지 않는다")
		void doesNotReloadBeforeTtlExpires() {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));
			cache.get();

			// when
			clock.advance(Duration.ofSeconds(59));
			cache.get();

			// then
			verify(recommendQueryMapper, times(1)).findProductFeatures();
		}

		@Test
		@DisplayName("TTL 이 0 이면 매번 다시 적재한다")
		void reloadsEveryTimeWhenTtlIsZero() {
			// given
			ProductFeatureCache cache = cache(Duration.ZERO);
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));

			// when
			cache.get();
			cache.get();
			cache.get();

			// then
			verify(recommendQueryMapper, times(3)).findProductFeatures();
			assertThat(cache.loadCount()).isEqualTo(3);
		}

		@Test
		@DisplayName("반환된 맵을 수정하려 하면 실패한다")
		void throwsWhenModifyingReturnedMap() {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));
			Map<Long, ProductFeature> features = cache.get();

			// when & then
			assertThatThrownBy(() -> features.put(2L, features.get(1L)))
					.isInstanceOf(UnsupportedOperationException.class);
		}

		@Test
		@DisplayName("여러 스레드가 동시에 접근해도 적재는 한 번만 일어난다")
		void loadsOnceUnderConcurrentAccess() throws Exception {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willAnswer(invocation -> {
				Thread.sleep(50);
				return List.of(row(1L));
			});
			int threadCount = 20;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);

			// when
			List<Future<Map<Long, ProductFeature>>> futures = IntStream.range(0, threadCount)
					.mapToObj(i -> executor.submit(cache::get))
					.toList();
			for (Future<Map<Long, ProductFeature>> future : futures) {
				future.get();
			}
			executor.shutdown();

			// then
			verify(recommendQueryMapper, times(1)).findProductFeatures();
			assertThat(cache.loadCount()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("handle()")
	class Handle {

		@Test
		@DisplayName("무효화 이벤트를 받으면 다음 호출에서 다시 적재한다")
		void reloadsAfterInvalidation() {
			// given
			ProductFeatureCache cache = cache(Duration.ofSeconds(60));
			given(recommendQueryMapper.findProductFeatures()).willReturn(List.of(row(1L)));
			cache.get();

			// when
			cache.handle(new ProductCatalogChangedEvent());
			cache.get();

			// then
			verify(recommendQueryMapper, times(2)).findProductFeatures();
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
