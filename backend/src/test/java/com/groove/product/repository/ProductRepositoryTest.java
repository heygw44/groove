package com.groove.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductGenre;
import com.groove.product.entity.ProductImage;
import com.groove.product.entity.ProductStatus;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class ProductRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private LabelRepository labelRepository;

	@Autowired
	private GenreRepository genreRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductImageRepository productImageRepository;

	@Autowired
	private ProductGenreRepository productGenreRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("artist·label 과 연관되어 저장되고 재조회하면 값이 유지된다")
		void persistsWithArtistAndLabel() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Label label = labelRepository.save(LabelFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist, label));

			// when
			flushAndClear();
			Product found = productRepository.findById(product.getId()).orElseThrow();

			// then
			assertThat(found.getPrice()).isEqualByComparingTo("45000.00");
			assertThat(found.getStatus()).isEqualTo(ProductStatus.ON_SALE);
			assertThat(found.getArtist().getId()).isEqualTo(artist.getId());
			assertThat(found.getLabel().getId()).isEqualTo(label.getId());
		}

		@Test
		@DisplayName("장르 2개를 추가하고 저장하면 두 건 모두 조회된다")
		void persistsGenres() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-product-save"));
			Genre soul = genreRepository.save(GenreFixture.create("Soul-product-save"));
			Product product = ProductFixture.create(artist);
			product.addGenre(jazz);
			product.addGenre(soul);

			// when
			Product saved = productRepository.save(product);
			flushAndClear();

			// then
			List<Genre> genres = productGenreRepository.findAllByProductId(saved.getId()).stream()
					.map(ProductGenre::getGenre)
					.toList();
			assertThat(genres).extracting(Genre::getName)
					.containsExactlyInAnyOrder("Jazz-product-save", "Soul-product-save");
		}

		@Test
		@DisplayName("replaceGenres() 로 교체 후 저장하면 새 장르만 조회된다")
		void replacesGenresOnSave() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-product-replace"));
			Genre rock = genreRepository.save(GenreFixture.create("Rock-product-replace"));
			Product product = ProductFixture.create(artist);
			product.addGenre(jazz);
			Product saved = productRepository.save(product);
			flushAndClear();

			// when
			Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
			reloaded.replaceGenres(List.of(rock));
			flushAndClear();

			// then
			List<Genre> genres = productGenreRepository.findAllByProductId(saved.getId()).stream()
					.map(ProductGenre::getGenre)
					.toList();
			assertThat(genres).extracting(Genre::getName).containsExactly("Rock-product-replace");
		}

		@Test
		@DisplayName("이미지를 순서 없이 추가해도 sortOrder 순으로 조회되고 첫 이미지만 대표다")
		void persistsImagesOrderedBySortOrder() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = ProductFixture.create(artist);
			product.addImage("https://cdn.groove.com/2.jpg", 2);
			product.addImage("https://cdn.groove.com/0.jpg", 0);
			product.addImage("https://cdn.groove.com/1.jpg", 1);
			Product saved = productRepository.save(product);

			// when
			flushAndClear();
			List<ProductImage> images = productImageRepository.findAllByProductIdOrderBySortOrderAsc(saved.getId());

			// then
			assertThat(images).extracting(ProductImage::getSortOrder).containsExactly(0, 1, 2);
			assertThat(images.get(0).isThumbnail()).isTrue();
			assertThat(images.get(1).isThumbnail()).isFalse();
			assertThat(images.get(2).isThumbnail()).isFalse();
		}
	}

	@Nested
	@DisplayName("findDetailById()")
	class FindDetailById {

		@Test
		@DisplayName("label 없이 저장해도 조회되고 artist·label·genre 연관관계가 초기화된다")
		void findsProductWithArtistLabelAndGenres() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Bill Evans-PRT-1"));
			Label label = labelRepository.save(LabelFixture.create());
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-PRT-1"));
			Genre soul = genreRepository.save(GenreFixture.create("Soul-PRT-1"));
			Product product = ProductFixture.create(artist, label);
			product.addGenre(jazz);
			product.addGenre(soul);
			product.addImage("https://cdn.groove.com/PRT-1-0.jpg", 0);
			product.addImage("https://cdn.groove.com/PRT-1-1.jpg", 1);
			Product saved = productRepository.save(product);
			flushAndClear();

			// when
			Optional<Product> found = productRepository.findDetailById(saved.getId());

			// then
			assertThat(found).isPresent();
			Product loaded = found.get();
			assertThat(Hibernate.isInitialized(loaded.getArtist())).isTrue();
			assertThat(Hibernate.isInitialized(loaded.getLabel())).isTrue();
			assertThat(Hibernate.isInitialized(loaded.getProductGenres())).isTrue();
			assertThat(loaded.getProductGenres()).allMatch(pg -> Hibernate.isInitialized(pg.getGenre()));
			assertThat(loaded.getArtist().getName()).isEqualTo("Bill Evans-PRT-1");
			assertThat(loaded.getLabel().getId()).isEqualTo(label.getId());
			assertThat(loaded.getProductGenres()).extracting(pg -> pg.getGenre().getName())
					.containsExactlyInAnyOrder("Jazz-PRT-1", "Soul-PRT-1");
		}

		@Test
		@DisplayName("label 없이 저장하면 label 은 null 로 조회된다")
		void findsProductWithoutLabel() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Miles Davis-PRT-2"));
			Product saved = productRepository.save(ProductFixture.create(artist));
			flushAndClear();

			// when
			Optional<Product> found = productRepository.findDetailById(saved.getId());

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getArtist().getName()).isEqualTo("Miles Davis-PRT-2");
			assertThat(found.get().getLabel()).isNull();
		}

		@Test
		@DisplayName("상세 조회에 필요한 데이터 접근이 SQL 3건 이하로 끝난다")
		void resolvesDetailWithinThreeStatements() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Herbie Hancock-PRT-3"));
			Genre jazz = genreRepository.save(GenreFixture.create("Jazz-PRT-3"));
			Product product = ProductFixture.create(artist);
			product.addGenre(jazz);
			product.addImage("https://cdn.groove.com/PRT-3-0.jpg", 0);
			product.addImage("https://cdn.groove.com/PRT-3-1.jpg", 1);
			Product saved = productRepository.save(product);
			stockRepository.save(Stock.create(saved, 9));
			flushAndClear();

			SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
			Statistics statistics = sessionFactory.getStatistics();
			statistics.clear();

			// when
			Product loaded = productRepository.findDetailById(saved.getId()).orElseThrow();
			List<ProductImage> images = productImageRepository.findAllByProductIdOrderBySortOrderAsc(saved.getId());
			int stockQuantity = stockRepository.findByProductId(saved.getId())
					.map(Stock::getQuantity)
					.orElse(0);
			String artistName = loaded.getArtist().getName();
			List<String> genreNames = loaded.getProductGenres().stream()
					.map(pg -> pg.getGenre().getName())
					.toList();

			// then
			assertThat(artistName).isEqualTo("Herbie Hancock-PRT-3");
			assertThat(genreNames).containsExactly("Jazz-PRT-3");
			assertThat(stockQuantity).isEqualTo(9);
			assertThat(images).extracting(ProductImage::getSortOrder).containsExactly(0, 1);
			assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
		}
	}

	@Nested
	@DisplayName("findAdminSummaries()")
	class FindAdminSummaries {

		@Test
		@DisplayName("HIDDEN 상품도 포함되고 재고 수량과 sortOrder 0 썸네일이 매핑된다")
		void mapsStockQuantityAndThumbnail() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = ProductFixture.create(artist, "Admin Summary Product");
			product.addImage("https://cdn.groove.com/thumb.jpg", 0);
			product.addImage("https://cdn.groove.com/sub.jpg", 1);
			product.hide();
			Product saved = productRepository.save(product);
			Stock stock = stockRepository.save(Stock.create(saved, 7));
			flushAndClear();

			// when
			Page<AdminProductSummaryResponse> page = productRepository.findAdminSummaries(null,
					PageRequest.of(0, 100));

			// then
			AdminProductSummaryResponse found = page.getContent().stream()
					.filter(summary -> summary.id().equals(saved.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(found.status()).isEqualTo(ProductStatus.HIDDEN);
			assertThat(found.thumbnailUrl()).isEqualTo("https://cdn.groove.com/thumb.jpg");
			assertThat(found.stockQuantity()).isEqualTo(stock.getQuantity());
		}

		@Test
		@DisplayName("status 로 필터링하면 해당 상태의 자기 상품만 조회된다")
		void filtersByStatus() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product onSale = productRepository.save(ProductFixture.create(artist, "Admin Filter On Sale"));
			stockRepository.save(Stock.create(onSale, 5));
			Product hidden = ProductFixture.create(artist, "Admin Filter Hidden");
			hidden.hide();
			Product savedHidden = productRepository.save(hidden);
			stockRepository.save(Stock.create(savedHidden, 3));
			flushAndClear();

			// when
			Page<AdminProductSummaryResponse> hiddenPage = productRepository.findAdminSummaries(
					ProductStatus.HIDDEN, PageRequest.of(0, 100));

			// then
			List<Long> hiddenIds = hiddenPage.getContent().stream()
					.map(AdminProductSummaryResponse::id)
					.toList();
			assertThat(hiddenIds).contains(savedHidden.getId());
			assertThat(hiddenIds).doesNotContain(onSale.getId());
		}
	}

	@Nested
	@DisplayName("refreshReviewStats()")
	class RefreshReviewStats {

		@Test
		@DisplayName("리뷰 평점을 반영해 평균과 개수를 갱신하고, 삭제하면 다시 계산된다")
		void recalculatesAverageAndCountAsReviewsChange() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Member reviewer1 = memberRepository.save(MemberFixture.create("refresh-stats-1@groove.com"));
			Member reviewer2 = memberRepository.save(MemberFixture.create("refresh-stats-2@groove.com"));
			Member reviewer3 = memberRepository.save(MemberFixture.create("refresh-stats-3@groove.com"));
			reviewRepository.save(ReviewFixture.create(product, reviewer1, 5));
			reviewRepository.save(ReviewFixture.create(product, reviewer2, 4));
			Review lowestRatedReview = reviewRepository.save(ReviewFixture.create(product, reviewer3, 3));
			flushAndClear();

			// when
			productRepository.refreshReviewStats(product.getId());
			entityManager.clear();
			Product afterThreeReviews = productRepository.findById(product.getId()).orElseThrow();

			// then
			assertThat(afterThreeReviews.getAverageRating()).isEqualByComparingTo("4.0");
			assertThat(afterThreeReviews.getReviewCount()).isEqualTo(3);

			// when: 평점 3점 리뷰를 삭제하면 평균이 다시 계산된다
			reviewRepository.delete(reviewRepository.findById(lowestRatedReview.getId()).orElseThrow());
			flushAndClear();
			productRepository.refreshReviewStats(product.getId());
			entityManager.clear();
			Product afterDeletingOne = productRepository.findById(product.getId()).orElseThrow();

			// then
			assertThat(afterDeletingOne.getAverageRating()).isEqualByComparingTo("4.5");
			assertThat(afterDeletingOne.getReviewCount()).isEqualTo(2);

			// when: 남은 리뷰를 모두 삭제하면 null·0 으로 되돌아간다
			reviewRepository.deleteAll(reviewRepository.findAll().stream()
					.filter(review -> review.getProduct().getId().equals(product.getId()))
					.toList());
			flushAndClear();
			productRepository.refreshReviewStats(product.getId());
			entityManager.clear();
			Product afterDeletingAll = productRepository.findById(product.getId()).orElseThrow();

			// then
			assertThat(afterDeletingAll.getAverageRating()).isNull();
			assertThat(afterDeletingAll.getReviewCount()).isZero();
		}
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
