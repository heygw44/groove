package com.groove.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSortType;
import com.groove.product.dto.ProductSuggestionResponse;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.support.MybatisTestSupport;
import com.groove.wishlist.entity.Wishlist;

import jakarta.persistence.EntityManager;

/** 키워드로 스코프를 좁혀 공유 테스트 DB에 남은 다른 테스트의 상품과 섞이지 않도록 한다. */
class ProductSearchMapperTest extends MybatisTestSupport {

	private static final String KEYWORD = "SMT";

	@Autowired
	private ProductSearchMapper productSearchMapper;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private EntityManager em;

	private Artist milesDavis;
	private Artist johnColtrane;
	private Label blueNote;
	private Genre jazz;
	private Genre rock;
	private Product kindOfBlue;
	private Product loveSupreme;
	private Product roundMidnight;
	private Product cheapRecord;

	@BeforeEach
	void setUp() {
		milesDavis = ArtistFixture.create("SMT Miles Davis");
		johnColtrane = ArtistFixture.create("SMT John Coltrane");
		blueNote = LabelFixture.create();
		jazz = GenreFixture.create("SMT Jazz");
		rock = GenreFixture.create("SMT Rock");
		em.persist(milesDavis);
		em.persist(johnColtrane);
		em.persist(blueNote);
		em.persist(jazz);
		em.persist(rock);

		kindOfBlue = ProductFixture.create(milesDavis, blueNote, "SMT Kind of Blue", new BigDecimal("30000.00"));
		kindOfBlue.addGenre(jazz);
		kindOfBlue.addImage("https://cdn.groove.com/kind-of-blue-0.jpg", 0);
		kindOfBlue.addImage("https://cdn.groove.com/kind-of-blue-1.jpg", 1);

		loveSupreme = ProductFixture.create(johnColtrane, "SMT A Love Supreme", new BigDecimal("45000.00"));
		loveSupreme.addGenre(jazz);
		loveSupreme.addGenre(rock);
		loveSupreme.addImage("https://cdn.groove.com/love-supreme-0.jpg", 0);

		roundMidnight = ProductFixture.create(milesDavis, blueNote, "SMT Round Midnight", new BigDecimal("60000.00"));
		roundMidnight.addGenre(rock);
		roundMidnight.addImage("https://cdn.groove.com/round-midnight-0.jpg", 0);

		Product hiddenAlbum = ProductFixture.create(milesDavis, blueNote, "SMT Hidden Album",
				new BigDecimal("40000.00"));
		hiddenAlbum.addGenre(jazz);
		hiddenAlbum.hide();

		cheapRecord = ProductFixture.create(johnColtrane, "SMT Cheap Record", new BigDecimal("15000.00"));
		cheapRecord.addGenre(rock);

		em.persist(kindOfBlue.getAlbum());
		em.persist(loveSupreme.getAlbum());
		em.persist(roundMidnight.getAlbum());
		em.persist(hiddenAlbum.getAlbum());
		em.persist(cheapRecord.getAlbum());
		em.persist(kindOfBlue);
		em.persist(loveSupreme);
		em.persist(roundMidnight);
		em.persist(hiddenAlbum);
		em.persist(cheapRecord);
		em.flush();
		em.clear();
	}

	private static ProductSearchCondition condition(String keyword, Long artistId, List<Long> genreIds, Long labelId,
			BigDecimal minPrice, BigDecimal maxPrice, ProductSortType sort, int page, int size) {
		return condition(keyword, artistId, genreIds, labelId, minPrice, maxPrice, sort, page, size, null);
	}

	private static ProductSearchCondition condition(String keyword, Long artistId, List<Long> genreIds, Long labelId,
			BigDecimal minPrice, BigDecimal maxPrice, ProductSortType sort, int page, int size, Long memberId) {
		return pressingCondition(keyword, artistId, genreIds, labelId, null, null, null, null, null, minPrice,
				maxPrice, sort, page, size, memberId);
	}

	private static ProductSearchCondition pressingCondition(String keyword, Long artistId, List<Long> genreIds,
			Long labelId, Long albumId, String country, Integer pressingYearFrom, Integer pressingYearTo,
			EditionType editionType, BigDecimal minPrice, BigDecimal maxPrice, ProductSortType sort, int page,
			int size, Long memberId) {
		return new ProductSearchCondition(keyword, artistId, genreIds, labelId, albumId, country, pressingYearFrom,
				pressingYearTo, editionType, extractBarcode(keyword), extractCatalogNoNormalized(keyword), minPrice,
				maxPrice, sort, page, size, memberId);
	}

	private static String extractBarcode(String keyword) {
		return keyword != null && keyword.matches("^\\d{8,14}$") ? keyword : null;
	}

	private static String extractCatalogNoNormalized(String keyword) {
		if (keyword == null || keyword.matches("^\\d{8,14}$") || !keyword.matches("^[A-Za-z0-9-]+$")) {
			return null;
		}
		return keyword.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
	}

	private static ProductSearchCondition scopedCondition(ProductSortType sort, int page, int size) {
		return condition(KEYWORD, null, null, null, null, null, sort, page, size);
	}

	@Nested
	@DisplayName("searchProducts()")
	class SearchProducts {

		@Test
		@DisplayName("HIDDEN 상품은 결과에서 제외된다")
		void excludesHiddenProducts() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.LATEST, 0, 20));

			// then
			assertThat(result).extracting(ProductSummaryResponse::title).doesNotContain("SMT Hidden Album");
		}

		@Test
		@DisplayName("제목 키워드로 검색하면 일치하는 상품만 반환한다")
		void filtersByTitleKeyword() {
			// given
			ProductSearchCondition cond = condition("SMT Kind of Blue", null, null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(kindOfBlue.getId());
		}

		@Test
		@DisplayName("아티스트명 키워드로 검색하면 해당 아티스트의 상품이 반환된다")
		void filtersByArtistNameKeyword() {
			// given
			ProductSearchCondition cond = condition("SMT John", null, null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(loveSupreme.getId(), cheapRecord.getId());
		}

		@Test
		@DisplayName("artistId 로 필터링하면 해당 아티스트의 상품만 반환한다")
		void filtersByArtistId() {
			// given
			ProductSearchCondition cond = condition(null, milesDavis.getId(), null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), roundMidnight.getId());
		}

		@Test
		@DisplayName("labelId 로 필터링하면 해당 레이블의 상품만 반환한다")
		void filtersByLabelId() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, null, blueNote.getId(), null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), roundMidnight.getId());
		}

		@Test
		@DisplayName("genreIds 로 필터링하면 해당 장르의 상품만 반환한다")
		void filtersByGenreIds() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, List.of(jazz.getId()), null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId());
		}

		@Test
		@DisplayName("genreIds 를 여러 개 지정하면 하나라도 포함된 상품을 중복 없이 반환한다")
		void filtersByMultipleGenreIdsWithoutDuplicates() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, List.of(jazz.getId(), rock.getId()), null, null,
					null, ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId(), roundMidnight.getId(),
							cheapRecord.getId());
		}

		@Test
		@DisplayName("genreIds 가 비어있으면 장르 필터 없이 전체 상품을 반환한다")
		void returnsAllWhenGenreIdsEmpty() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, List.of(), null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId(), roundMidnight.getId(),
							cheapRecord.getId());
		}

		@Test
		@DisplayName("minPrice·maxPrice 로 필터링하면 가격 범위 내 상품만 반환한다")
		void filtersByPriceRange() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, null, null, new BigDecimal("20000"),
					new BigDecimal("50000"), ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId());
		}

		@Test
		@DisplayName("keyword·genreIds·가격대를 조합하면 모든 조건을 만족하는 상품만 반환한다")
		void filtersByCombinedConditions() {
			// given
			ProductSearchCondition cond = condition("SMT Kind", null, List.of(jazz.getId()), null,
					new BigDecimal("20000"), new BigDecimal("40000"), ProductSortType.LATEST, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(kindOfBlue.getId());
		}

		@ParameterizedTest
		@EnumSource(ProductSortType.class)
		@DisplayName("정렬 기준과 무관하게 예외 없이 결과를 반환한다")
		void sortsWithoutError(ProductSortType sortType) {
			// when & then
			assertThat(productSearchMapper.searchProducts(scopedCondition(sortType, 0, 20))).isNotEmpty();
		}

		@Test
		@DisplayName("PRICE_ASC 정렬이면 가격 오름차순으로 반환한다")
		void sortsByPriceAscending() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.PRICE_ASC, 0, 20));

			// then
			assertThat(result).extracting(ProductSummaryResponse::price).isSorted();
		}

		@Test
		@DisplayName("PRICE_DESC 정렬이면 가격 내림차순으로 반환한다")
		void sortsByPriceDescending() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.PRICE_DESC, 0, 20));

			// then
			assertThat(result).extracting(ProductSummaryResponse::price)
					.isSortedAccordingTo((a, b) -> b.compareTo(a));
		}

		@Test
		@DisplayName("LATEST 정렬이면 id 내림차순으로 반환한다")
		void sortsByLatest() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.LATEST, 0, 20));

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.isSortedAccordingTo((a, b) -> Long.compare(b, a));
		}

		@Test
		@DisplayName("size·page 로 페이징하면 offset 이후 항목만 반환한다")
		void paginatesWithOffset() {
			// given
			List<ProductSummaryResponse> firstPage = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.LATEST, 0, 2));

			// when
			List<ProductSummaryResponse> secondPage = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.LATEST, 1, 2));

			// then
			assertThat(firstPage).hasSize(2);
			assertThat(secondPage).isNotEmpty();
			assertThat(secondPage).extracting(ProductSummaryResponse::id)
					.doesNotContainAnyElementsOf(firstPage.stream().map(ProductSummaryResponse::id).toList());
		}

		@Test
		@DisplayName("sortOrder 0 인 이미지가 썸네일 URL 로 반환된다")
		void returnsFirstImageAsThumbnail() {
			// given
			ProductSearchCondition cond = condition("SMT Kind of Blue", null, null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			ProductSummaryResponse result = productSearchMapper.searchProducts(cond).get(0);

			// then
			assertThat(result.thumbnailUrl()).isEqualTo("https://cdn.groove.com/kind-of-blue-0.jpg");
		}

		@Test
		@DisplayName("이미지가 없는 상품은 썸네일 URL 이 null 이다")
		void returnsNullThumbnailWhenNoImage() {
			// given
			ProductSearchCondition cond = condition("SMT Cheap Record", null, null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			ProductSummaryResponse result = productSearchMapper.searchProducts(cond).get(0);

			// then
			assertThat(result.thumbnailUrl()).isNull();
		}

		@Test
		@DisplayName("레이블이 없는 상품은 labelName 이 null 이고 평점·리뷰수는 기본값이다")
		void returnsDefaultsForLabelAndReview() {
			// given
			ProductSearchCondition cond = condition("SMT Cheap Record", null, null, null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			ProductSummaryResponse result = productSearchMapper.searchProducts(cond).get(0);

			// then
			assertThat(result.labelName()).isNull();
			assertThat(result.averageRating()).isNull();
			assertThat(result.reviewCount()).isZero();
		}
	}

	@Nested
	@DisplayName("countProducts()")
	class CountProducts {

		@Test
		@DisplayName("HIDDEN 상품을 제외한 개수를 반환한다")
		void countsExcludingHidden() {
			// when
			long count = productSearchMapper.countProducts(scopedCondition(ProductSortType.LATEST, 0, 20));

			// then
			assertThat(count).isEqualTo(4);
		}

		@Test
		@DisplayName("동일 조건의 searchProducts 결과 개수와 일치한다")
		void matchesSearchResultSize() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, List.of(jazz.getId()), null, null, null,
					ProductSortType.LATEST, 0, 20);

			// when
			long count = productSearchMapper.countProducts(cond);
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(count).isEqualTo(result.size());
		}

		@Test
		@DisplayName("genreIds 를 여러 개 지정해도 중복 없이 카운트한다")
		void matchesSearchResultSizeForMultipleGenres() {
			// given
			ProductSearchCondition cond = condition(KEYWORD, null, List.of(jazz.getId(), rock.getId()), null, null,
					null, ProductSortType.LATEST, 0, 20);

			// when
			long count = productSearchMapper.countProducts(cond);

			// then
			assertThat(count).isEqualTo(4);
		}
	}

	@Nested
	@DisplayName("wishlisted")
	class Wishlisted {

		@Test
		@DisplayName("memberId 가 없으면 모든 상품의 wishlisted 가 null 이다")
		void isNullWhenMemberIdAbsent() {
			// given
			Member member = MemberFixture.create("smt-wish@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					scopedCondition(ProductSortType.LATEST, 0, 20));

			// then
			assertThat(result).extracting(ProductSummaryResponse::wishlisted).containsOnlyNulls();
		}

		@Test
		@DisplayName("memberId 를 지정하면 위시리스트에 담긴 상품만 true 를 반환한다")
		void marksOnlyWishlistedProductsTrue() {
			// given
			Member member = MemberFixture.create("smt-wish@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(
					condition(KEYWORD, null, null, null, null, null, ProductSortType.LATEST, 0, 20,
							member.getId()));

			// then
			assertThat(result).filteredOn(r -> r.id().equals(kindOfBlue.getId()))
					.extracting(ProductSummaryResponse::wishlisted)
					.containsExactly(true);
			assertThat(result).filteredOn(r -> r.id().equals(loveSupreme.getId()))
					.extracting(ProductSummaryResponse::wishlisted)
					.containsExactly(false);
		}
	}

	@Nested
	@DisplayName("rating/popular 정렬")
	class RatingAndPopularSort {

		private static final String RATING_KEYWORD = "SMTR";

		private Product highRatedFewReviews;
		private Product lowRatedManyReviews;
		private Product noReviews;

		@BeforeEach
		void setUpRatingProducts() {
			Artist artist = ArtistFixture.create("SMTR Artist");
			em.persist(artist);

			highRatedFewReviews = ProductFixture.create(artist, "SMTR High Rated", new BigDecimal("10000.00"));
			lowRatedManyReviews = ProductFixture.create(artist, "SMTR Low Rated", new BigDecimal("10000.00"));
			noReviews = ProductFixture.create(artist, "SMTR No Reviews", new BigDecimal("10000.00"));
			em.persist(highRatedFewReviews.getAlbum());
			em.persist(lowRatedManyReviews.getAlbum());
			em.persist(noReviews.getAlbum());
			em.persist(highRatedFewReviews);
			em.persist(lowRatedManyReviews);
			em.persist(noReviews);
			em.flush();

			addReview(highRatedFewReviews, 5);
			addReview(lowRatedManyReviews, 3);
			addReview(lowRatedManyReviews, 3);
			addReview(lowRatedManyReviews, 3);
			em.flush();
			em.clear();

			productRepository.refreshReviewStats(highRatedFewReviews.getId());
			productRepository.refreshReviewStats(lowRatedManyReviews.getId());
			productRepository.refreshReviewStats(noReviews.getId());
			em.clear();
		}

		private void addReview(Product product, int rating) {
			Member reviewer = MemberFixture.create(
					"smtr-" + product.getId() + "-" + System.nanoTime() + "@groove.com");
			em.persist(reviewer);
			em.persist(ReviewFixture.create(em.find(Product.class, product.getId()), reviewer, rating));
		}

		private void addPaidOrder(Product product, int quantity) {
			Member buyer = MemberFixture.create("smtr-buyer-" + System.nanoTime() + "@groove.com");
			em.persist(buyer);
			Order order = OrderFixture.create(buyer, "20260905-SMTR" + System.nanoTime() % 100000);
			order.addItem(em.find(Product.class, product.getId()), quantity);
			OrderFixture.markPaid(order);
			em.persist(order);
		}

		@Test
		@DisplayName("RATING 정렬이면 평균 평점 내림차순, 그 다음 리뷰 개수 내림차순으로 반환하고 리뷰 없는 상품은 마지막이다")
		void sortsByAverageRatingDescending() {
			// given
			ProductSearchCondition cond = condition(RATING_KEYWORD, null, null, null, null, null,
					ProductSortType.RATING, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactly(highRatedFewReviews.getId(), lowRatedManyReviews.getId(), noReviews.getId());
		}

		@Test
		@DisplayName("POPULAR 정렬이면 판매 수량 내림차순으로 반환하고, 수량이 같으면 리뷰 개수 내림차순으로 반환한다")
		void sortsBySoldQuantityDescending() {
			// given: 리뷰는 lowRatedManyReviews 가 더 많지만 판매 수량은 highRatedFewReviews 와 같다(동률 태그)
			addPaidOrder(highRatedFewReviews, 5);
			addPaidOrder(lowRatedManyReviews, 5);
			em.flush();
			em.clear();

			ProductSearchCondition cond = condition(RATING_KEYWORD, null, null, null, null, null,
					ProductSortType.POPULAR, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then: 판매 수량이 같으면 리뷰 개수(3 > 1)가 더 많은 lowRatedManyReviews 가 앞선다
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactly(lowRatedManyReviews.getId(), highRatedFewReviews.getId(), noReviews.getId());
		}
	}

	@Nested
	@DisplayName("프레싱 조건 검색")
	class PressingFilters {

		private static final String BARCODE_US = "9990000000001";
		private static final String BARCODE_JP = "9990000000002";

		private Album album;
		private Product usOriginal;
		private Product jpRemaster;
		private Product usLimited;

		@BeforeEach
		void setUpPressings() {
			Artist artist = ArtistFixture.create("SMTP Artist");
			em.persist(artist);
			album = AlbumFixture.create(artist, "SMTP Multi Pressing Album");
			em.persist(album);

			usOriginal = ProductFixture.createPressing(album, artist, "SMTP US Original", new BigDecimal("40000"),
					"US", 1959, "SMTP-US1", BARCODE_US, EditionType.STANDARD);
			jpRemaster = ProductFixture.createPressing(album, artist, "SMTP JP Remaster", new BigDecimal("35000"),
					"JP", 2022, "SMTP-JP1", BARCODE_JP, EditionType.REMASTER);
			usLimited = ProductFixture.createPressing(album, artist, "SMTP US Limited", new BigDecimal("60000"),
					"US", 1980, "SMTP-US2", null, EditionType.LIMITED);
			em.persist(usOriginal);
			em.persist(jpRemaster);
			em.persist(usLimited);

			// 제목에 바코드 숫자를 담아도 LIKE 로 걸리지 않는지 확인하기 위한 다른 상품
			Product decoy = ProductFixture.createPressing(album, artist, "SMTP " + BARCODE_US + " Decoy",
					new BigDecimal("10000"), "KR", 2000, "SMTP-DECOY", "9990000000099", EditionType.STANDARD);
			em.persist(decoy);
			em.flush();
			em.clear();
		}

		@Test
		@DisplayName("country 로 필터링하면 해당 국가의 프레싱만 반환한다")
		void filtersByCountry() {
			// given
			ProductSearchCondition cond = pressingCondition(null, null, null, null, null, "JP", null, null, null,
					null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).contains(jpRemaster.getId())
					.doesNotContain(usOriginal.getId(), usLimited.getId());
		}

		@Test
		@DisplayName("pressingYearFrom·pressingYearTo 로 필터링하면 연도 범위 내 프레싱만 반환한다")
		void filtersByPressingYearRange() {
			// given
			ProductSearchCondition cond = pressingCondition(null, null, null, null, null, null, 1990, 2023, null,
					null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).contains(jpRemaster.getId())
					.doesNotContain(usOriginal.getId(), usLimited.getId());
		}

		@Test
		@DisplayName("editionType 으로 필터링하면 해당 에디션의 프레싱만 반환한다")
		void filtersByEditionType() {
			// given
			ProductSearchCondition cond = pressingCondition(null, null, null, null, null, null, null, null,
					EditionType.LIMITED, null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).contains(usLimited.getId())
					.doesNotContain(usOriginal.getId(), jpRemaster.getId());
		}

		@Test
		@DisplayName("albumId 로 필터링하면 같은 앨범의 프레싱만 반환한다")
		void filtersByAlbumId() {
			// given
			ProductSearchCondition cond = pressingCondition(null, null, null, null, album.getId(), null, null, null,
					null, null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.contains(usOriginal.getId(), jpRemaster.getId(), usLimited.getId());
		}

		@Test
		@DisplayName("바코드 keyword 는 정확일치만 되고 제목에 같은 숫자가 있어도 LIKE 로 잡히지 않는다")
		void matchesBarcodeExactlyWithoutLike() {
			// given
			ProductSearchCondition cond = pressingCondition(BARCODE_US, null, null, null, null, null, null, null,
					null, null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(usOriginal.getId());
		}

		@Test
		@DisplayName("카탈로그 번호 keyword 는 제목/아티스트명 LIKE 와 OR 로 정확일치 검색된다")
		void matchesCatalogNoExactlyOrWithLike() {
			// given
			ProductSearchCondition cond = pressingCondition("SMTP-US2", null, null, null, null, null, null, null,
					null, null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(usLimited.getId());
		}

		@Test
		@DisplayName("countProducts() 는 searchProducts() 와 같은 개수를 반환한다")
		void countMatchesSearchResultSize() {
			// given
			ProductSearchCondition cond = pressingCondition(null, null, null, null, album.getId(), null, null, null,
					null, null, null, ProductSortType.LATEST, 0, 20, null);

			// when
			long count = productSearchMapper.countProducts(cond);
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(count).isEqualTo(result.size());
		}
	}

	@Nested
	@DisplayName("findAlbumPressings()")
	class FindAlbumPressings {

		private Album album;
		private Product oldest;
		private Product newest;
		private Product noYear;
		private Product hidden;

		@BeforeEach
		void setUpAlbumPressings() {
			Artist artist = ArtistFixture.create("SMTF Artist");
			em.persist(artist);
			album = AlbumFixture.create(artist, "SMTF Album");
			em.persist(album);

			newest = ProductFixture.createPressing(album, artist, "SMTF Newest", new BigDecimal("30000"), "JP",
					2022, "SMTF-JP", "9990000000011", EditionType.REMASTER);
			oldest = ProductFixture.createPressing(album, artist, "SMTF Oldest", new BigDecimal("40000"), "US",
					1959, "SMTF-US", "9990000000012", EditionType.STANDARD);
			noYear = ProductFixture.createPressing(album, artist, "SMTF No Year", new BigDecimal("50000"), "KR",
					null, "SMTF-KR", "9990000000013", EditionType.STANDARD);
			hidden = ProductFixture.createPressing(album, artist, "SMTF Hidden", new BigDecimal("20000"), "US",
					1970, "SMTF-HIDDEN", "9990000000014", EditionType.STANDARD);
			hidden.hide();
			em.persist(newest);
			em.persist(oldest);
			em.persist(noYear);
			em.persist(hidden);
			em.flush();
			em.clear();
		}

		@Test
		@DisplayName("HIDDEN 상품은 제외하고 pressingYear 오름차순, null 은 마지막으로 반환한다")
		void returnsPressingsOrderedByYearWithNullsLast() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.findAlbumPressings(album.getId());

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactly(oldest.getId(), newest.getId(), noYear.getId());
		}

		@Test
		@DisplayName("wishlisted 는 항상 null 이다")
		void alwaysReturnsNullWishlisted() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.findAlbumPressings(album.getId());

			// then
			assertThat(result).extracting(ProductSummaryResponse::wishlisted).containsOnlyNulls();
		}

		@Test
		@DisplayName("다른 앨범의 프레싱은 포함되지 않는다")
		void excludesOtherAlbums() {
			// when
			List<ProductSummaryResponse> result = productSearchMapper.findAlbumPressings(album.getId());

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).doesNotContain(kindOfBlue.getId());
		}
	}

	@Nested
	@DisplayName("suggestProducts()")
	class SuggestProducts {

		private static final String SUGGEST_KEYWORD = "SGT";

		private Product titlePrefixMatch;
		private Product artistPrefixMatch;
		private Product middleMatch;
		private Product hiddenMatch;

		@BeforeEach
		void setUpSuggestions() {
			Artist middleNamedArtist = ArtistFixture.create("Middle SGT Artist");
			Artist prefixNamedArtist = ArtistFixture.create("SGT Prefix Artist");
			Artist plainArtist = ArtistFixture.create("Plain Artist");
			em.persist(middleNamedArtist);
			em.persist(prefixNamedArtist);
			em.persist(plainArtist);

			titlePrefixMatch = ProductFixture.create(middleNamedArtist, "SGT Prefix Title",
					new BigDecimal("10000.00"));
			artistPrefixMatch = ProductFixture.create(prefixNamedArtist, "Random Title", new BigDecimal("20000.00"));
			middleMatch = ProductFixture.create(plainArtist, "Something SGT Middle", new BigDecimal("30000.00"));
			hiddenMatch = ProductFixture.create(plainArtist, "SGT Hidden Product", new BigDecimal("40000.00"));
			hiddenMatch.hide();

			em.persist(titlePrefixMatch.getAlbum());
			em.persist(artistPrefixMatch.getAlbum());
			em.persist(middleMatch.getAlbum());
			em.persist(hiddenMatch.getAlbum());
			em.persist(titlePrefixMatch);
			em.persist(artistPrefixMatch);
			em.persist(middleMatch);
			em.persist(hiddenMatch);
			em.flush();
			em.clear();
		}

		@Test
		@DisplayName("제목에 키워드가 포함되면 결과에 반환된다")
		void filtersByTitleKeyword() {
			// when
			List<ProductSuggestionResponse.Item> result = productSearchMapper.suggestProducts(SUGGEST_KEYWORD, 5);

			// then
			assertThat(result).extracting(ProductSuggestionResponse.Item::id).contains(titlePrefixMatch.getId());
		}

		@Test
		@DisplayName("아티스트명에 키워드가 포함되면 결과에 반환된다")
		void filtersByArtistNameKeyword() {
			// when
			List<ProductSuggestionResponse.Item> result = productSearchMapper.suggestProducts(SUGGEST_KEYWORD, 5);

			// then
			assertThat(result).extracting(ProductSuggestionResponse.Item::id).contains(artistPrefixMatch.getId());
		}

		@Test
		@DisplayName("제목·아티스트명이 키워드로 시작하는 상품이 중간에 포함된 상품보다 앞선다")
		void ordersPrefixMatchesBeforeMiddleMatches() {
			// when
			List<ProductSuggestionResponse.Item> result = productSearchMapper.suggestProducts(SUGGEST_KEYWORD, 5);

			// then
			assertThat(result).extracting(ProductSuggestionResponse.Item::id)
					.containsExactly(titlePrefixMatch.getId(), artistPrefixMatch.getId(), middleMatch.getId());
		}

		@Test
		@DisplayName("HIDDEN 상품은 결과에서 제외된다")
		void excludesHiddenProducts() {
			// when
			List<ProductSuggestionResponse.Item> result = productSearchMapper.suggestProducts(SUGGEST_KEYWORD, 5);

			// then
			assertThat(result).extracting(ProductSuggestionResponse.Item::id).doesNotContain(hiddenMatch.getId());
		}

		@Test
		@DisplayName("limit 을 넘는 결과는 잘라서 반환한다")
		void limitsResultSize() {
			// when
			List<ProductSuggestionResponse.Item> result = productSearchMapper.suggestProducts(SUGGEST_KEYWORD, 2);

			// then
			assertThat(result).extracting(ProductSuggestionResponse.Item::id)
					.containsExactly(titlePrefixMatch.getId(), artistPrefixMatch.getId());
		}
	}
}
