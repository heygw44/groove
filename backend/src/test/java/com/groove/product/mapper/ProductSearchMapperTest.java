package com.groove.product.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.member.entity.Member;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSortType;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Artist;
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
		return new ProductSearchCondition(keyword, artistId, genreIds, labelId, minPrice, maxPrice, sort, page, size,
				memberId);
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
		@DisplayName("POPULAR 정렬이면 리뷰 개수 내림차순으로 반환한다")
		void sortsByReviewCountDescending() {
			// given
			ProductSearchCondition cond = condition(RATING_KEYWORD, null, null, null, null, null,
					ProductSortType.POPULAR, 0, 20);

			// when
			List<ProductSummaryResponse> result = productSearchMapper.searchProducts(cond);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactly(lowRatedManyReviews.getId(), highRatedFewReviews.getId(), noReviews.getId());
		}
	}
}
