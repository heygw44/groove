package com.groove.recommend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ProductViewLogFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.CoPurchaseRow;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.entity.ProductViewLog;
import com.groove.support.MybatisTestSupport;
import com.groove.wishlist.entity.Wishlist;

import jakarta.persistence.EntityManager;

class RecommendQueryMapperTest extends MybatisTestSupport {

	@Autowired
	private RecommendQueryMapper recommendQueryMapper;

	@Autowired
	private EntityManager em;

	private Artist artist;
	private Product kindOfBlue;
	private Product loveSupreme;
	private Product hiddenAlbum;

	@BeforeEach
	void setUp() {
		artist = ArtistFixture.create("RQM Artist");
		em.persist(artist);

		kindOfBlue = ProductFixture.create(artist, "RQM Kind of Blue", new BigDecimal("30000.00"));
		loveSupreme = ProductFixture.create(artist, "RQM A Love Supreme", new BigDecimal("45000.00"));
		hiddenAlbum = ProductFixture.create(artist, "RQM Hidden Album", new BigDecimal("40000.00"));
		hiddenAlbum.hide();

		em.persist(kindOfBlue.getAlbum());
		em.persist(loveSupreme.getAlbum());
		em.persist(hiddenAlbum.getAlbum());
		em.persist(kindOfBlue);
		em.persist(loveSupreme);
		em.persist(hiddenAlbum);
		em.flush();
		em.clear();
	}

	@Nested
	@DisplayName("findSummariesByIds()")
	class FindSummariesByIds {

		@Test
		@DisplayName("ids 순서와 무관하게 해당 상품들을 전부 조회한다")
		void findsAllRequestedIdsRegardlessOfOrder() {
			// given
			List<Long> ids = List.of(loveSupreme.getId(), kindOfBlue.getId());

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(ids, null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id)
					.containsExactlyInAnyOrder(kindOfBlue.getId(), loveSupreme.getId());
		}

		@Test
		@DisplayName("HIDDEN 상품은 제외한다")
		void excludesHiddenProducts() {
			// given
			List<Long> ids = List.of(kindOfBlue.getId(), hiddenAlbum.getId());

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(ids, null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::id).containsExactly(kindOfBlue.getId());
		}

		@Test
		@DisplayName("memberId 가 null 이면 wishlisted 가 null 이다")
		void wishlistedIsNullWhenMemberIdAbsent() {
			// given
			Member member = MemberFixture.create("rqm-wish@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(
					List.of(kindOfBlue.getId()), null);

			// then
			assertThat(result).extracting(ProductSummaryResponse::wishlisted).containsExactly((Boolean) null);
		}

		@Test
		@DisplayName("memberId 가 있으면 위시 여부가 반영된다")
		void reflectsWishlistStatusWhenMemberIdPresent() {
			// given
			Member member = MemberFixture.create("rqm-wish2@groove.com");
			em.persist(member);
			em.persist(Wishlist.create(member, kindOfBlue));
			em.flush();
			em.clear();

			// when
			List<ProductSummaryResponse> result = recommendQueryMapper.findSummariesByIds(
					List.of(kindOfBlue.getId(), loveSupreme.getId()), member.getId());

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
	@DisplayName("findRecentProductIds()")
	class FindRecentProductIds {

		@Test
		@DisplayName("폴백 쿼리가 상품별 최신 조회순으로 반환한다")
		void returnsProductIdsOrderedByLatestViewedAt() {
			// given
			Member member = MemberFixture.create("rqm-recent@groove.com");
			em.persist(member);

			LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
			persistView(member, kindOfBlue, now.minusMinutes(10));
			persistView(member, loveSupreme, now.minusMinutes(5));
			persistView(member, kindOfBlue, now.minusMinutes(1));
			em.flush();
			em.clear();

			// when
			List<Long> result = recommendQueryMapper.findRecentProductIds(member.getId(), 20);

			// then
			assertThat(result).containsExactly(kindOfBlue.getId(), loveSupreme.getId());
		}

		private void persistView(Member member, Product product, LocalDateTime viewedAt) {
			ProductViewLog viewLog = ProductViewLogFixture.create(
					em.find(Member.class, member.getId()), em.find(Product.class, product.getId()), viewedAt);
			em.persist(viewLog);
		}
	}

	@Nested
	@DisplayName("countCoPurchases()")
	class CountCoPurchases {

		private Product productA;
		private Product productB;
		private Product productC;

		@BeforeEach
		void setUpProducts() {
			productA = ProductFixture.create(artist, "CoPurchase A", new BigDecimal("10000.00"));
			productB = ProductFixture.create(artist, "CoPurchase B", new BigDecimal("20000.00"));
			productC = ProductFixture.create(artist, "CoPurchase C", new BigDecimal("30000.00"));
			em.persist(productA.getAlbum());
			em.persist(productB.getAlbum());
			em.persist(productC.getAlbum());
			em.persist(productA);
			em.persist(productB);
			em.persist(productC);
			em.flush();
		}

		@Test
		@DisplayName("PAID 주문에 상품 세 개가 함께 담기면 모든 쌍이 양방향으로 집계된다")
		void countsEachDirectionOfPairWhenThreeProductsInOneOrder() {
			// given
			Member member = MemberFixture.create("copurchase-3items@groove.com");
			em.persist(member);
			Order order = OrderFixture.createWithItems(member, List.of(productA, productB, productC));
			order = OrderFixture.markPaid(order);
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<CoPurchaseRow> result = countMyPairs();

			// then
			assertThat(result).containsExactlyInAnyOrder(
					new CoPurchaseRow(productA.getId(), productB.getId(), 1L),
					new CoPurchaseRow(productB.getId(), productA.getId(), 1L),
					new CoPurchaseRow(productA.getId(), productC.getId(), 1L),
					new CoPurchaseRow(productC.getId(), productA.getId(), 1L),
					new CoPurchaseRow(productB.getId(), productC.getId(), 1L),
					new CoPurchaseRow(productC.getId(), productB.getId(), 1L));
		}

		@Test
		@DisplayName("두 주문에 같은 상품 쌍이 담기면 카운트가 합산된다")
		void sumsCountAcrossMultipleOrders() {
			// given
			Member member = MemberFixture.create("copurchase-2orders@groove.com");
			em.persist(member);
			Order paidOrder = OrderFixture.markPaid(OrderFixture.createWithItems(member, List.of(productA, productB)));
			Order deliveredOrder = OrderFixture.markDelivered(
					OrderFixture.createWithItems(member, List.of(productA, productB)));
			em.persist(paidOrder);
			em.persist(deliveredOrder);
			em.flush();
			em.clear();

			// when
			List<CoPurchaseRow> result = countMyPairs();

			// then
			assertThat(result).filteredOn(r -> r.productId().equals(productA.getId())
							&& r.otherProductId().equals(productB.getId()))
					.extracting(CoPurchaseRow::count)
					.containsExactly(2L);
		}

		@Test
		@DisplayName("PENDING·CANCELED 주문은 집계되지 않는다")
		void excludesPendingAndCanceledOrders() {
			// given
			Member member = MemberFixture.create("copurchase-excluded@groove.com");
			em.persist(member);
			Order pendingOrder = OrderFixture.createWithItems(member, List.of(productA, productB));
			Order canceledOrder = OrderFixture.createWithItems(member, List.of(productA, productB));
			canceledOrder.cancel("사용자 변심");
			em.persist(pendingOrder);
			em.persist(canceledOrder);
			em.flush();
			em.clear();

			// when
			List<CoPurchaseRow> result = countMyPairs();

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("상품이 하나만 담긴 주문은 쌍이 나오지 않는다")
		void producesNoPairForOrderWithSingleItem() {
			// given
			Member member = MemberFixture.create("copurchase-single@groove.com");
			em.persist(member);
			Order order = OrderFixture.markPaid(OrderFixture.createWithItems(member, List.of(productA)));
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<CoPurchaseRow> result = countMyPairs();

			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("sinceAt 이 미래면 내 상품 쌍이 조회되지 않는다")
		void excludesOrdersBeforeFutureSinceAt() {
			// given
			Member member = MemberFixture.create("copurchase-future@groove.com");
			em.persist(member);
			Order order = OrderFixture.markPaid(OrderFixture.createWithItems(member, List.of(productA, productB)));
			em.persist(order);
			em.flush();
			em.clear();

			// when
			List<CoPurchaseRow> result = recommendQueryMapper.countCoPurchases(
					LocalDateTime.now().plusDays(1));
			List<Long> myIds = List.of(productA.getId(), productB.getId());

			// then
			assertThat(result.stream().filter(r -> myIds.contains(r.productId())).toList()).isEmpty();
		}

		private List<CoPurchaseRow> countMyPairs() {
			List<Long> myIds = List.of(productA.getId(), productB.getId(), productC.getId());
			LocalDateTime sinceAt = LocalDateTime.now().minusDays(1);
			return recommendQueryMapper.countCoPurchases(sinceAt).stream()
					.filter(r -> myIds.contains(r.productId()))
					.toList();
		}
	}

	@Nested
	@DisplayName("findProductFeatures()")
	class FindProductFeatures {

		@Test
		@DisplayName("장르가 두 개면 genreIds 에 두 id 가 모두 담긴다")
		void includesAllGenreIdsWhenProductHasMultipleGenres() {
			// given
			Label label = LabelFixture.create();
			em.persist(label);
			Genre rock = GenreFixture.create("Rock");
			Genre jazz = GenreFixture.create("Jazz");
			em.persist(rock);
			em.persist(jazz);

			Album album = AlbumFixture.create(artist, "PF Multi Genre");
			em.persist(album);
			Product product = Product.create(album, "PF Multi Genre", artist,
					label, LocalDate.of(2020, 5, 1), "180g", "Black", null, null, null, null, null,
					new BigDecimal("30000.00"), "설명");
			product.addGenre(rock);
			product.addGenre(jazz);
			em.persist(product);
			em.flush();
			em.clear();

			// when
			ProductFeatureRow row = findMyRow(product.getId());

			// then
			assertThat(Arrays.stream(row.genreIds().split(",")).map(Long::parseLong).toList())
					.containsExactlyInAnyOrder(rock.getId(), jazz.getId());
			assertThat(row.artistId()).isEqualTo(artist.getId());
			assertThat(row.labelId()).isEqualTo(label.getId());
			assertThat(row.releaseYear()).isEqualTo(2020);
		}

		@Test
		@DisplayName("review_count·sold_quantity 를 그대로 담는다")
		void includesReviewCountAndSoldQuantity() {
			// given
			Album album = AlbumFixture.create(artist, "PF Review Sold");
			em.persist(album);
			Product product = Product.create(album, "PF Review Sold", artist, null, LocalDate.of(2023, 1, 1),
					"180g", "Black", null, null, null, null, null, new BigDecimal("30000.00"), "설명");
			ReflectionTestUtils.setField(product, "reviewCount", 7);
			ReflectionTestUtils.setField(product, "soldQuantity", 42L);
			em.persist(product);
			em.flush();
			em.clear();

			// when
			ProductFeatureRow row = findMyRow(product.getId());

			// then
			assertThat(row.reviewCount()).isEqualTo(7);
			assertThat(row.soldQuantity()).isEqualTo(42L);
		}

		@Test
		@DisplayName("장르가 없으면 genreIds 가 null 이다")
		void genreIdsIsNullWhenProductHasNoGenre() {
			// given
			Album album = AlbumFixture.create(artist, "PF No Genre");
			em.persist(album);
			Product product = Product.create(album, "PF No Genre", artist, null,
					LocalDate.of(2021, 3, 1), "180g", "Black", null, null, null, null, null,
					new BigDecimal("25000.00"), "설명");
			em.persist(product);
			em.flush();
			em.clear();

			// when
			ProductFeatureRow row = findMyRow(product.getId());

			// then
			assertThat(row.genreIds()).isNull();
			assertThat(row.labelId()).isNull();
			assertThat(row.releaseYear()).isEqualTo(2021);
		}

		@Test
		@DisplayName("HIDDEN 상품도 결과에 포함된다")
		void includesHiddenProduct() {
			// given & when
			ProductFeatureRow row = findMyRow(hiddenAlbum.getId());

			// then
			assertThat(row.status()).isEqualTo(ProductStatus.HIDDEN);
		}

		@Test
		@DisplayName("상품을 앨범과 함께 저장하면 행의 albumId 가 그 앨범 id 와 같다")
		void albumIdMatchesProductAlbum() {
			// given
			Album album = AlbumFixture.create(artist, "PF Album Id");
			em.persist(album);
			Product product = Product.create(album, "PF Album Id", artist, null,
					LocalDate.of(2022, 6, 1), "180g", "Black", null, null, null, null, null,
					new BigDecimal("28000.00"), "설명");
			em.persist(product);
			em.flush();
			em.clear();

			// when
			ProductFeatureRow row = findMyRow(product.getId());

			// then
			assertThat(row.albumId()).isEqualTo(album.getId());
		}

		@Test
		@DisplayName("release_date 없이 pressing_year 만 있으면 그 연도로 연대를 구한다")
		void usesPressingYearWhenReleaseDateIsAbsent() {
			// given
			Album album = AlbumFixture.create(artist, "PF Imported");
			em.persist(album);
			Product product = Product.createImported(album, "PF Imported", artist, null, "US", 1975,
					"CS 8163", "888880123456", null, new BigDecimal("30000.00"), null, null);
			em.persist(product);
			em.flush();
			em.clear();

			// when
			ProductFeatureRow row = findMyRow(product.getId());

			// then
			assertThat(row.releaseYear()).isEqualTo(1975);
		}

		private ProductFeatureRow findMyRow(Long productId) {
			return recommendQueryMapper.findProductFeatures().stream()
					.filter(r -> r.productId().equals(productId))
					.findFirst()
					.orElseThrow();
		}
	}

	@Nested
	@DisplayName("findPopularProductIds()")
	class FindPopularProductIds {

		@Test
		@DisplayName("ON_SALE 상품만 평점 desc(null 마지막) → 리뷰수 desc → 최신순으로 뽑는다")
		void ordersByRatingThenReviewCountForOnSaleProductsOnly() {
			// given
			Product highRating = ratedProduct("FPI High Rating", new BigDecimal("4.5"), 10);
			Product moreReviews = ratedProduct("FPI More Reviews", new BigDecimal("4.5"), 20);
			Product noRating = ratedProduct("FPI No Rating", null, 100);
			Product soldOut = ratedProduct("FPI Sold Out", new BigDecimal("5.0"), 5);
			soldOut.markSoldOut();
			em.persist(highRating.getAlbum());
			em.persist(moreReviews.getAlbum());
			em.persist(noRating.getAlbum());
			em.persist(soldOut.getAlbum());
			em.persist(highRating);
			em.persist(moreReviews);
			em.persist(noRating);
			em.persist(soldOut);
			em.flush();
			em.clear();

			// when
			List<Long> myIds = List.of(highRating.getId(), moreReviews.getId(), noRating.getId(), soldOut.getId());
			List<Long> result = recommendQueryMapper.findPopularProductIds(1000).stream()
					.filter(myIds::contains)
					.toList();

			// then
			assertThat(result).containsExactly(moreReviews.getId(), highRating.getId(), noRating.getId());
		}

		private Product ratedProduct(String title, BigDecimal rating, int reviewCount) {
			Product product = ProductFixture.create(artist, title, new BigDecimal("30000.00"));
			ReflectionTestUtils.setField(product, "averageRating", rating);
			ReflectionTestUtils.setField(product, "reviewCount", reviewCount);
			return product;
		}
	}
}
