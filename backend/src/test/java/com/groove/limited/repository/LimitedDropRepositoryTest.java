package com.groove.limited.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import org.springframework.data.domain.Sort;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropSummaryRow;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.DataJpaTestSupport;

class LimitedDropRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductImageRepository productImageRepository;

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
					LimitedDropStatus.OPEN, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

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
					PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")));

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
			// MySQL datetime(6) 이 나노초를 반올림하므로 초 단위로 잘라 저장한다.
			LocalDateTime openAt = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
			LocalDateTime closeAt = openAt.plusDays(1);
			LimitedDrop drop = LimitedDropFixture.withCloseAt(
					LimitedDropFixture.withOpenAt(LimitedDropFixture.open(product, 50), openAt), closeAt);
			LimitedDrop saved = limitedDropRepository.save(LimitedDropFixture.withSoldCount(drop, 7));
			Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

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
			assertThat(summary.openAt()).isEqualTo(openAt);
			assertThat(summary.closeAt()).isEqualTo(closeAt);
			assertThat(summary.status()).isEqualTo(LimitedDropStatus.OPEN);
			assertThat(summary.createdAt()).isNotNull();
		}
	}

	@Nested
	@DisplayName("findPublicSummaries()")
	class FindPublicSummaries {

		@Test
		@DisplayName("statuses 로 필터링하면 저장한 드롭 중 해당 상태만 포함한다")
		void returnsOnlyMatchingStatusAmongSavedDrops() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product scheduledProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품11"));
			Product openProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품12"));
			LimitedDrop scheduledDrop = limitedDropRepository.save(LimitedDropFixture.scheduled(scheduledProduct));
			LimitedDrop openDrop = limitedDropRepository.save(LimitedDropFixture.open(openProduct, 50));

			// when
			List<LimitedDropSummaryRow> rows = limitedDropRepository.findPublicSummaries(
					List.of(LimitedDropStatus.OPEN));

			// then
			List<Long> ids = rows.stream().map(LimitedDropSummaryRow::id).toList();
			assertThat(ids).contains(openDrop.getId()).doesNotContain(scheduledDrop.getId());
		}

		@Test
		@DisplayName("상품이 HIDDEN 이면 드롭이 있어도 제외한다")
		void excludesDropsOfHiddenProduct() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product hiddenProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품13"));
			hiddenProduct.hide();
			productRepository.save(hiddenProduct);
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(hiddenProduct));

			// when
			List<LimitedDropSummaryRow> rows = limitedDropRepository.findPublicSummaries(
					List.of(LimitedDropStatus.SCHEDULED));

			// then
			List<Long> ids = rows.stream().map(LimitedDropSummaryRow::id).toList();
			assertThat(ids).doesNotContain(drop.getId());
		}

		@Test
		@DisplayName("openAt 오름차순으로 정렬한다")
		void ordersByOpenAtAscending() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product laterProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품14"));
			Product earlierProduct = productRepository.save(ProductFixture.create(artist, "한정반 상품15"));
			LocalDateTime base = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS);
			LimitedDrop laterDrop = limitedDropRepository.save(LimitedDropFixture.withOpenAt(
					LimitedDropFixture.scheduled(laterProduct), base.plusDays(5)));
			LimitedDrop earlierDrop = limitedDropRepository.save(LimitedDropFixture.withOpenAt(
					LimitedDropFixture.scheduled(earlierProduct), base));

			// when
			List<LimitedDropSummaryRow> rows = limitedDropRepository.findPublicSummaries(
					List.of(LimitedDropStatus.SCHEDULED));

			// then
			List<Long> orderedIds = rows.stream()
					.map(LimitedDropSummaryRow::id)
					.filter(id -> id.equals(laterDrop.getId()) || id.equals(earlierDrop.getId()))
					.toList();
			assertThat(orderedIds).containsExactly(earlierDrop.getId(), laterDrop.getId());
		}

		@Test
		@DisplayName("sortOrder 0 인 이미지를 썸네일로 채운다")
		void populatesThumbnailFromFirstImage() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, "한정반 상품16"));
			productImageRepository.save(ProductImage.of(product, "https://cdn.groove.com/0.jpg", 0));
			productImageRepository.save(ProductImage.of(product, "https://cdn.groove.com/1.jpg", 1));
			LimitedDrop drop = limitedDropRepository.save(LimitedDropFixture.scheduled(product));

			// when
			List<LimitedDropSummaryRow> rows = limitedDropRepository.findPublicSummaries(
					List.of(LimitedDropStatus.SCHEDULED));

			// then
			LimitedDropSummaryRow row = rows.stream()
					.filter(r -> r.id().equals(drop.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(row.thumbnailUrl()).isEqualTo("https://cdn.groove.com/0.jpg");
			assertThat(row.artistName()).isEqualTo(artist.getName());
			assertThat(row.productTitle()).isEqualTo("한정반 상품16");
		}
	}
}
