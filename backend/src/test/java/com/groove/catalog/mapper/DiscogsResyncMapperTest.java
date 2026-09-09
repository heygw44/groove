package com.groove.catalog.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.catalog.dto.DiscogsResyncCandidate;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.support.MybatisTestSupport;

import jakarta.persistence.EntityManager;

class DiscogsResyncMapperTest extends MybatisTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 0, 0);
	private static final LocalDateTime STALE_BEFORE = NOW.minusHours(6);
	private static final LocalDateTime VIEW_SINCE = NOW.minusDays(7);

	@Autowired
	private DiscogsResyncMapper discogsResyncMapper;

	@Autowired
	private EntityManager em;

	private Artist artist;

	private Product persistWithRelease(String title, long releaseId, LocalDateTime syncedAt) {
		Product product = ProductFixture.create(artist, title, new BigDecimal("30000"));
		em.persist(product.getAlbum());
		em.persist(product);
		ReflectionTestUtils.setField(product, "discogsReleaseId", releaseId);
		ReflectionTestUtils.setField(product, "discogsSyncedAt", syncedAt);
		return product;
	}

	private void view(Product product, int count, LocalDateTime lastViewedAt) {
		for (int i = 0; i < count; i++) {
			em.persist(ProductViewLogFixture.createAnonymous(product, lastViewedAt.minusMinutes(i)));
		}
	}

	@Nested
	@DisplayName("findCandidates()")
	class FindCandidates {

		@Test
		@DisplayName("discogs_release_id 가 없는 자체 등록 상품은 후보에서 뺀다")
		void excludesSelfRegisteredProducts() {
			// given
			artist = ArtistFixture.create("DRM Artist Self");
			em.persist(artist);
			Product selfProduct = ProductFixture.create(artist, "DRM Self Product", new BigDecimal("30000"));
			em.persist(selfProduct.getAlbum());
			em.persist(selfProduct);
			Product importedProduct = persistWithRelease("DRM Imported Product", 5001L, null);
			em.flush();
			em.clear();

			// when
			List<DiscogsResyncCandidate> result = discogsResyncMapper.findCandidates(NOW, VIEW_SINCE, false, false,
					100);

			// then
			assertThat(result).extracting(DiscogsResyncCandidate::productId)
					.contains(importedProduct.getId())
					.doesNotContain(selfProduct.getId());
		}

		@Test
		@DisplayName("신선한(discogs_synced_at 이 최근인) 상품은 후보에서 뺀다")
		void excludesFreshProducts() {
			// given
			artist = ArtistFixture.create("DRM Artist Fresh");
			em.persist(artist);
			Product freshProduct = persistWithRelease("DRM Fresh Product", 5002L, NOW.minusHours(1));
			Product staleProduct = persistWithRelease("DRM Stale Product", 5003L, NOW.minusHours(7));
			em.flush();
			em.clear();

			// when
			List<DiscogsResyncCandidate> result = discogsResyncMapper.findCandidates(STALE_BEFORE, VIEW_SINCE, false,
					false, 100);

			// then
			assertThat(result).extracting(DiscogsResyncCandidate::productId)
					.contains(staleProduct.getId())
					.doesNotContain(freshProduct.getId());
		}

		@Test
		@DisplayName("visibleOnly 가 true 면 HIDDEN 상품을 뺀다")
		void excludesHiddenWhenVisibleOnly() {
			// given
			artist = ArtistFixture.create("DRM Artist Hidden");
			em.persist(artist);
			Product hiddenProduct = persistWithRelease("DRM Hidden Product", 5004L, null);
			hiddenProduct.hide();
			Product onSaleProduct = persistWithRelease("DRM OnSale Product", 5005L, null);
			em.flush();
			em.clear();

			// when
			List<DiscogsResyncCandidate> visibleOnly = discogsResyncMapper.findCandidates(NOW, VIEW_SINCE, true,
					false, 100);
			List<DiscogsResyncCandidate> includingHidden = discogsResyncMapper.findCandidates(NOW, VIEW_SINCE, false,
					false, 100);

			// then
			assertThat(visibleOnly).extracting(DiscogsResyncCandidate::productId)
					.contains(onSaleProduct.getId())
					.doesNotContain(hiddenProduct.getId());
			assertThat(includingHidden).extracting(DiscogsResyncCandidate::productId)
					.contains(hiddenProduct.getId(), onSaleProduct.getId());
		}

		@Test
		@DisplayName("viewPriority 가 true 면 조회수가 많은 상품을 먼저 정렬한다")
		void ordersByViewCountWhenViewPriority() {
			// given
			artist = ArtistFixture.create("DRM Artist Priority");
			em.persist(artist);
			Product lessViewed = persistWithRelease("DRM Less Viewed", 5006L, null);
			Product moreViewed = persistWithRelease("DRM More Viewed", 5007L, null);
			view(lessViewed, 1, NOW.minusDays(1));
			view(moreViewed, 5, NOW.minusHours(1));
			em.flush();
			em.clear();

			// when
			List<DiscogsResyncCandidate> result = discogsResyncMapper.findCandidates(NOW, VIEW_SINCE, false, true,
					100);

			// then
			List<Long> order = result.stream().map(DiscogsResyncCandidate::productId).toList();
			assertThat(order.indexOf(moreViewed.getId())).isLessThan(order.indexOf(lessViewed.getId()));
		}

		@Test
		@DisplayName("viewPriority 가 false 면 discogs_synced_at 오름차순(오래된 것부터)으로 정렬한다")
		void ordersByOldestSyncedAtFirstWhenNotViewPriority() {
			// given
			artist = ArtistFixture.create("DRM Artist Order");
			em.persist(artist);
			Product oldest = persistWithRelease("DRM Oldest", 5008L, NOW.minusHours(10));
			Product newer = persistWithRelease("DRM Newer", 5009L, NOW.minusHours(9));
			em.flush();
			em.clear();

			// when
			List<Long> order = discogsResyncMapper.findCandidates(NOW, VIEW_SINCE, false, false, 1000).stream()
					.map(DiscogsResyncCandidate::productId)
					.filter(id -> id.equals(oldest.getId()) || id.equals(newer.getId()))
					.toList();

			// then
			assertThat(order).containsExactly(oldest.getId(), newer.getId());
		}
	}

	@Nested
	@DisplayName("countStale()")
	class CountStale {

		@Test
		@DisplayName("discogs_release_id 가 있고 stale 한 상품만 센다")
		void countsOnlyStaleImportedProducts() {
			// given
			artist = ArtistFixture.create("DRM Artist Count");
			em.persist(artist);
			persistWithRelease("DRM Count Stale 1", 5010L, NOW.minusHours(7));
			persistWithRelease("DRM Count Stale 2", 5011L, null);
			persistWithRelease("DRM Count Fresh", 5012L, NOW.minusHours(1));
			em.flush();
			em.clear();

			// when
			// 같은 파라미터로 두 번 호출하면 MyBatis 세션 로컬 캐시가 두 번째 호출을 가로채 첫 결과를 재사용한다
			// (JPA 쓰기는 다른 세션이라 캐시 무효화 대상이 아니다) - 그래서 호출은 테스트당 한 번만 한다.
			long count = discogsResyncMapper.countStale(STALE_BEFORE);

			// then
			assertThat(count).isEqualTo(2);
		}
	}
}
