package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropMeta;
import com.groove.limited.service.LimitedDropMetaCache;
import com.groove.limited.service.LimitedDropScheduleService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 캐시 동작(TTL/적재 횟수) 자체는 LimitedDropMetaCacheTest 가 단위로 검증한다. 여기서는 test 프로필의
 * meta-cache-ttl 이 0 이라 캐시가 항상 다시 적재하므로 evict() 가 실제로 불렸는지는 가르지 못한다 — 대신
 * LimitedDropScheduleService.open() 이 호출하는 무효화 경로가 실제 스프링 빈 조합에서 예외 없이 끝까지
 * 이어지고, 그 뒤 조회한 메타가 바뀐 상태를 반영하는지를 본다.
 */
class LimitedDropMetaCacheInvalidationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedDropScheduleService limitedDropScheduleService;

	@Autowired
	private LimitedDropMetaCache limitedDropMetaCache;

	@Nested
	@DisplayName("open() 뒤 메타 조회")
	class AfterOpen {

		@Test
		@DisplayName("open() 으로 상태가 바뀐 뒤 다시 조회하면 OPEN 메타를 돌려준다")
		void reflectsOpenStatusAfterScheduleServiceOpens() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			stockRepository.saveAndFlush(StockFixture.create(product, 10));
			LocalDateTime now = LocalDateTime.now();
			LimitedDrop scheduled = LimitedDropFixture.scheduled(product, 10, 2);
			LimitedDropFixture.withOpenAt(scheduled, now.minusMinutes(1));
			LimitedDrop drop = limitedDropRepository.saveAndFlush(scheduled);
			Long dropId = drop.getId();

			Optional<LimitedDropMeta> beforeOpen = limitedDropMetaCache.get(dropId);
			assertThat(beforeOpen).isPresent();
			assertThat(beforeOpen.get().status()).isEqualTo(LimitedDropStatus.SCHEDULED);

			// when
			boolean opened = limitedDropScheduleService.open(dropId, now);

			// then
			assertThat(opened).isTrue();
			Optional<LimitedDropMeta> afterOpen = limitedDropMetaCache.get(dropId);
			assertThat(afterOpen).isPresent();
			assertThat(afterOpen.get().status()).isEqualTo(LimitedDropStatus.OPEN);
		}
	}
}
