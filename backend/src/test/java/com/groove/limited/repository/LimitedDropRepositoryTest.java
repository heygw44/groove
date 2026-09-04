package com.groove.limited.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
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

	@Nested
	@DisplayName("findWithProductById()")
	class FindWithProductById {

		@Test
		@DisplayName("존재하는 드롭이면 상품을 즉시 조회할 수 있는 드롭을 반환한다")
		void returnsDropWithAccessibleProduct() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품5"));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			// when
			Optional<LimitedDrop> found = limitedDropRepository.findWithProductById(drop.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getProduct().getTitle()).isEqualTo("한정반 상품5");
		}
	}

	@Nested
	@DisplayName("findAdminSummaries()")
	class FindAdminSummaries {

		@Test
		@DisplayName("상태로 필터링하면 저장한 드롭 중 해당 상태만 포함한다")
		void returnsOnlyMatchingStatusAmongSavedDrops() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product scheduledProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품6"));
			Product openProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품7"));
			LimitedDrop scheduledDrop = limitedDropRepository.save(LimitedDropFixture.scheduled(scheduledProduct));
			LimitedDrop openDrop = limitedDropRepository.save(LimitedDropFixture.open(openProduct, 50));

			// when
			Page<AdminLimitedDropSummaryResponse> page = limitedDropRepository.findAdminSummaries(
					LimitedDropStatus.OPEN, PageRequest.of(0, 20));

			// then
			List<Long> ids = page.getContent().stream().map(AdminLimitedDropSummaryResponse::id).toList();
			assertThat(ids).contains(openDrop.getId()).doesNotContain(scheduledDrop.getId());
		}

		@Test
		@DisplayName("상태를 지정하지 않으면 저장한 드롭이 상태와 무관하게 모두 포함된다")
		void includesAllStatusesWhenStatusIsNull() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product scheduledProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품8"));
			Product openProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품9"));
			LimitedDrop scheduledDrop = limitedDropRepository.save(LimitedDropFixture.scheduled(scheduledProduct));
			LimitedDrop openDrop = limitedDropRepository.save(LimitedDropFixture.open(openProduct, 50));

			// when
			Page<AdminLimitedDropSummaryResponse> page = limitedDropRepository.findAdminSummaries(null,
					PageRequest.of(0, 20));

			// then
			List<Long> ids = page.getContent().stream().map(AdminLimitedDropSummaryResponse::id).toList();
			assertThat(ids).contains(scheduledDrop.getId(), openDrop.getId());
		}

		@Test
		@DisplayName("조회 결과에 상품명과 판매 수량 등 요약 필드가 채워진다")
		void populatesSummaryProjectionFields() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품10"));
			LimitedDrop drop = LimitedDropFixture.withSoldCount(LimitedDropFixture.open(product, 50), 7);
			LimitedDrop saved = limitedDropRepository.save(drop);
			Pageable pageable = PageRequest.of(0, 20);

			// when
			Page<AdminLimitedDropSummaryResponse> page = limitedDropRepository.findAdminSummaries(
					LimitedDropStatus.OPEN, pageable);

			// then
			AdminLimitedDropSummaryResponse summary = page.getContent().stream()
					.filter(s -> s.id().equals(saved.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(summary.productId()).isEqualTo(product.getId());
			assertThat(summary.productTitle()).isEqualTo("한정반 상품10");
			assertThat(summary.totalQuantity()).isEqualTo(50);
			assertThat(summary.soldCount()).isEqualTo(7);
			assertThat(summary.perMemberLimit()).isEqualTo(saved.getPerMemberLimit());
			assertThat(summary.openAt()).isEqualTo(saved.getOpenAt());
			assertThat(summary.closeAt()).isEqualTo(saved.getCloseAt());
			assertThat(summary.status()).isEqualTo(LimitedDropStatus.OPEN);
			assertThat(summary.createdAt()).isNotNull();
		}
	}
}
