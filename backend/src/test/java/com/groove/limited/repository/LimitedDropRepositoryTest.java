package com.groove.limited.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class LimitedDropRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 상품에 드롭을 두 번 저장하면 유니크 제약 위반이 발생한다")
		void throwsWhenProductDuplicated() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품1"));
			limitedDropRepository.saveAndFlush(LimitedDropFixture.scheduled(product));

			// when & then
			assertThatThrownBy(() -> limitedDropRepository.saveAndFlush(LimitedDropFixture.scheduled(product)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("existsByProductIdAndStatusNot()")
	class ExistsByProductIdAndStatusNot {

		@Test
		@DisplayName("CLOSED 가 아닌 드롭이 있으면 true 를 반환한다")
		void returnsTrueWhenActiveDropExists() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품2"));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			// when
			boolean exists = limitedDropRepository.existsByProductIdAndStatusNot(product.getId(),
					LimitedDropStatus.CLOSED);

			// then
			assertThat(exists).isTrue();
			assertThat(drop.getId()).isNotNull();
		}

		@Test
		@DisplayName("드롭이 CLOSED 상태면 false 를 반환한다")
		void returnsFalseWhenDropClosed() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품3"));
			LimitedDrop drop = LimitedDropFixture.withStatus(LimitedDropFixture.scheduled(product),
					LimitedDropStatus.CLOSED);
			limitedDropRepository.save(drop);

			// when
			boolean exists = limitedDropRepository.existsByProductIdAndStatusNot(product.getId(),
					LimitedDropStatus.CLOSED);

			// then
			assertThat(exists).isFalse();
		}
	}

	@Nested
	@DisplayName("findByProductId()")
	class FindByProductId {

		@Test
		@DisplayName("존재하는 상품이면 드롭을 반환한다")
		void returnsDropWhenExists() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품4"));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			// when
			Optional<LimitedDrop> found = limitedDropRepository.findByProductId(product.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(drop.getId());
		}
	}
}
