package com.groove.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class StockRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("findWithProductByProductId()")
	class FindWithProductByProductId {

		@Test
		@DisplayName("존재하면 product 를 함께 조회한다")
		void findsStockWithProduct() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Stock saved = stockRepository.save(StockFixture.create(product, 5));
			flushAndClear();

			// when
			Optional<Stock> found = stockRepository.findWithProductByProductId(product.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(saved.getId());
			assertThat(found.get().getProduct().getId()).isEqualTo(product.getId());
		}

		@Test
		@DisplayName("존재하지 않으면 빈 값을 반환한다")
		void returnsEmptyWhenNotFound() {
			// when
			Optional<Stock> found = stockRepository.findWithProductByProductId(-1L);

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 상품에 재고를 두 번 저장하면 유니크 제약 위반이 발생한다")
		void throwsWhenProductIdDuplicated() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			stockRepository.saveAndFlush(StockFixture.create(product, 1));

			// when & then
			assertThatThrownBy(() -> stockRepository.saveAndFlush(StockFixture.create(product, 2)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}

		@Test
		@DisplayName("수정 후 flush 하면 version 이 증가한다")
		void incrementsVersionAfterUpdate() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Stock saved = stockRepository.save(StockFixture.create(product, 5));
			flushAndClear();
			Stock reloaded = stockRepository.findById(saved.getId()).orElseThrow();
			Long initialVersion = reloaded.getVersion();

			// when
			reloaded.increase(1);
			stockRepository.saveAndFlush(reloaded);
			entityManager.clear();

			// then
			Stock afterUpdate = stockRepository.findById(saved.getId()).orElseThrow();
			assertThat(afterUpdate.getVersion()).isEqualTo(initialVersion + 1);
		}
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
