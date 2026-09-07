package com.groove;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.cart.dto.CartItemAddRequest;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.NotificationFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.notification.entity.Notification;
import com.groove.notification.repository.NotificationRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.repository.ReviewRepository;
import com.groove.support.IntegrationTestSupport;
import com.groove.wishlist.dto.WishlistAddRequest;

import jakarta.persistence.EntityManagerFactory;

/**
 * N+1 회귀 감시용 통합 테스트.
 * Hibernate {@code Statistics.getPrepareStatementCount()} 로 요청 한 번에 실제 실행된 SQL 수를 센다
 * ({@code generate_statistics} 는 application-test.yml 에 켜져 있다).
 * N+1 은 리포지토리가 아니라 {@code XxxResponse.from()} 이 지연 연관을 역참조하는 시점에 터지고,
 * {@code @DataJpaTest} 는 그 시점을 재현하지 못하므로 통합 계층에서 잰다.
 * 상한 단언만으로는 부족해서 연관 개수를 늘려도 쿼리 수가 그대로인지를 함께 단언한다.
 * {@code default_batch_fetch_size} 가 켜져 있어 지연 연관을 놓쳐도 쿼리가 폭증하지 않고 +1 만 늘기 때문에,
 * 이 불변성 단언이 실질적인 방어선이다.
 */
@AutoConfigureMockMvc
class NPlusOneRegressionTest extends IntegrationTestSupport {

	private static final String SUFFIX = "-NPLUS1";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	EntityManagerFactory entityManagerFactory;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Autowired
	AddressRepository addressRepository;

	@Autowired
	ArtistRepository artistRepository;

	@Autowired
	AlbumRepository albumRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	GenreRepository genreRepository;

	@Autowired
	StockRepository stockRepository;

	@Autowired
	ReviewRepository reviewRepository;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	LimitedDropRepository limitedDropRepository;

	@Nested
	@DisplayName("GET /api/v1/admin/products/{id}")
	class AdminProductDetail {

		@Test
		@DisplayName("장르가 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenGenresGrow() throws Exception {
			// given
			String adminBearer = adminBearer();
			Product productWithOneGenre = seedProductWithGenres(1);
			Product productWithFiveGenres = seedProductWithGenres(5);

			// when
			long queriesForOne = countQueries(get("/api/v1/admin/products/{id}", productWithOneGenre.getId())
					.header(HttpHeaders.AUTHORIZATION, adminBearer));
			long queriesForFive = countQueries(get("/api/v1/admin/products/{id}", productWithFiveGenres.getId())
					.header(HttpHeaders.AUTHORIZATION, adminBearer));

			// then
			mockMvc.perform(get("/api/v1/admin/products/{id}", productWithFiveGenres.getId())
							.header(HttpHeaders.AUTHORIZATION, adminBearer))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.genres.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(3);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/cart")
	class CartRead {

		@Test
		@DisplayName("담긴 상품이 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenItemCountGrows() throws Exception {
			// given
			String bearerForOne = memberBearer();
			addProductsToCart(bearerForOne, 1);
			String bearerForFive = memberBearer();
			addProductsToCart(bearerForFive, 5);

			// when
			long queriesForOne = countQueries(
					get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearerForOne));
			long queriesForFive = countQueries(
					get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearerForFive));

			// then
			mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, bearerForFive))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(4);
		}

		private void addProductsToCart(String bearer, int count) throws Exception {
			for (int i = 0; i < count; i++) {
				Product product = seedProduct(10);
				mockMvc.perform(post("/api/v1/cart/items")
								.header(HttpHeaders.AUTHORIZATION, bearer)
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(
										new CartItemAddRequest(product.getId(), 1))))
						.andExpect(status().isCreated());
			}
		}
	}

	@Nested
	@DisplayName("GET /api/v1/orders/{id}")
	class OrderDetail {

		@Test
		@DisplayName("주문 항목이 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenItemCountGrows() throws Exception {
			// given
			String bearerForOne = memberBearer();
			long orderIdWithOneItem = createOrderWithItems(bearerForOne, currentMember, 1);
			String bearerForFive = memberBearer();
			long orderIdWithFiveItems = createOrderWithItems(bearerForFive, currentMember, 5);

			// when
			long queriesForOne = countQueries(get("/api/v1/orders/{id}", orderIdWithOneItem)
					.header(HttpHeaders.AUTHORIZATION, bearerForOne));
			long queriesForFive = countQueries(get("/api/v1/orders/{id}", orderIdWithFiveItems)
					.header(HttpHeaders.AUTHORIZATION, bearerForFive));

			// then
			mockMvc.perform(get("/api/v1/orders/{id}", orderIdWithFiveItems)
							.header(HttpHeaders.AUTHORIZATION, bearerForFive))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(3);
		}

		private long createOrderWithItems(String bearer, Member owner, int itemCount) throws Exception {
			Address address = addressRepository.save(AddressFixture.create(owner));
			List<Long> cartItemIds = new ArrayList<>();
			for (int i = 0; i < itemCount; i++) {
				Product product = seedProduct(10);
				MvcResult addResult = mockMvc.perform(post("/api/v1/cart/items")
								.header(HttpHeaders.AUTHORIZATION, bearer)
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(
										new CartItemAddRequest(product.getId(), 1))))
						.andExpect(status().isCreated())
						.andReturn();
				cartItemIds.add(objectMapper.readTree(addResult.getResponse().getContentAsString())
						.path("data").path("id").asLong());
			}
			OrderCreateRequest createRequest = new OrderCreateRequest(cartItemIds, null, null, address.getId(),
					null);
			MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, bearer)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			return objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderId").asLong();
		}
	}

	@Nested
	@DisplayName("GET /api/v1/wishlist")
	class WishlistList {

		@Test
		@DisplayName("담긴 상품이 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenItemCountGrows() throws Exception {
			// given
			String bearerForOne = memberBearer();
			addProductsToWishlist(bearerForOne, 1);
			String bearerForFive = memberBearer();
			addProductsToWishlist(bearerForFive, 5);

			// when
			long queriesForOne = countQueries(
					get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, bearerForOne));
			long queriesForFive = countQueries(
					get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, bearerForFive));

			// then
			mockMvc.perform(get("/api/v1/wishlist").header(HttpHeaders.AUTHORIZATION, bearerForFive))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(3);
		}

		private void addProductsToWishlist(String bearer, int count) throws Exception {
			for (int i = 0; i < count; i++) {
				Product product = seedProduct(10);
				mockMvc.perform(post("/api/v1/wishlist")
								.header(HttpHeaders.AUTHORIZATION, bearer)
								.contentType(MediaType.APPLICATION_JSON)
								.content(objectMapper.writeValueAsString(
										new WishlistAddRequest(product.getId()))))
						.andExpect(status().isCreated());
			}
		}
	}

	@Nested
	@DisplayName("GET /api/v1/products/{productId}/reviews")
	class ReviewList {

		@Test
		@DisplayName("리뷰가 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenReviewCountGrows() throws Exception {
			// given
			Product productWithOneReview = seedProduct(10);
			addReviews(productWithOneReview, 1);
			Product productWithFiveReviews = seedProduct(10);
			addReviews(productWithFiveReviews, 5);

			// when
			long queriesForOne = countQueries(
					get("/api/v1/products/{productId}/reviews", productWithOneReview.getId()));
			long queriesForFive = countQueries(
					get("/api/v1/products/{productId}/reviews", productWithFiveReviews.getId()));

			// then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews", productWithFiveReviews.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(2);
		}

		private void addReviews(Product product, int count) {
			for (int i = 0; i < count; i++) {
				Member reviewer = memberRepository.save(
						MemberFixture.create("review-list" + SUFFIX + "-" + UUID.randomUUID() + "@groove.com"));
				reviewRepository.save(ReviewFixture.create(product, reviewer));
			}
		}
	}

	@Nested
	@DisplayName("GET /api/v1/limited-drops")
	class LimitedDropList {

		@Test
		@DisplayName("드롭이 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenDropCountGrows() throws Exception {
			// given: 목록은 생성자 프로젝션이라 다른 테스트가 남긴 드롭이 섞여 있어도 쿼리 수 비교엔 영향이 없다
			createScheduledDrop();

			// when
			long queriesForOne = countQueries(get("/api/v1/limited-drops"));
			for (int i = 0; i < 4; i++) {
				createScheduledDrop();
			}
			long queriesForFive = countQueries(get("/api/v1/limited-drops"));

			// then
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(1);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/limited-drops/{id}")
	class LimitedDropDetail {

		@Test
		@DisplayName("드롭 상세 조회 쿼리 수가 상한 이내다")
		void keepsQueryCountWithinLimit() throws Exception {
			// given
			LimitedDrop drop = createScheduledDrop();

			// when
			long queries = countQueries(get("/api/v1/limited-drops/{id}", drop.getId()));

			// then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", drop.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id").value(drop.getId().intValue()));
			assertThat(queries).isLessThanOrEqualTo(2);
		}
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/notifications")
	class NotificationList {

		@Test
		@DisplayName("알림이 늘어도 쿼리 수가 그대로다")
		void keepsQueryCountWhenNotificationCountGrows() throws Exception {
			// given
			String bearerForOne = memberBearer();
			addNotifications(currentMember, 1);
			String bearerForFive = memberBearer();
			addNotifications(currentMember, 5);

			// when
			long queriesForOne = countQueries(
					get("/api/v1/members/me/notifications").header(HttpHeaders.AUTHORIZATION, bearerForOne));
			long queriesForFive = countQueries(
					get("/api/v1/members/me/notifications").header(HttpHeaders.AUTHORIZATION, bearerForFive));

			// then
			mockMvc.perform(get("/api/v1/members/me/notifications")
							.header(HttpHeaders.AUTHORIZATION, bearerForFive))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content.length()").value(5));
			assertThat(queriesForFive).isEqualTo(queriesForOne);
			assertThat(queriesForFive).isLessThanOrEqualTo(1);
		}

		private void addNotifications(Member member, int count) {
			for (int i = 0; i < count; i++) {
				Product product = seedProduct(10);
				Notification notification = NotificationFixture.forProduct(member, product);
				notificationRepository.save(notification);
			}
		}
	}

	private Member currentMember;

	private Statistics statistics() {
		return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
	}

	private long countQueries(MockHttpServletRequestBuilder request) throws Exception {
		Statistics statistics = statistics();
		statistics.clear();
		mockMvc.perform(request).andExpect(status().isOk());
		return statistics.getPrepareStatementCount();
	}

	private String memberBearer() {
		Member member = memberRepository.save(
				MemberFixture.create("nplus1-" + UUID.randomUUID() + SUFFIX + "@groove.com"));
		currentMember = member;
		return "Bearer " + jwtProvider.createAccessToken(member.getId(), MemberRole.USER);
	}

	private String adminBearer() {
		Member admin = memberRepository.save(
				MemberFixture.createAdmin("nplus1-admin-" + UUID.randomUUID() + SUFFIX + "@groove.com"));
		return "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create("Artist" + SUFFIX + "-" + UUID.randomUUID()));
		Product createdProduct = ProductFixture.create(artist, "Product" + SUFFIX + "-" + UUID.randomUUID());
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Product seedProductWithGenres(int genreCount) {
		Artist artist = artistRepository.save(ArtistFixture.create("Artist" + SUFFIX + "-" + UUID.randomUUID()));
		Product createdProduct = ProductFixture.create(artist, "Product" + SUFFIX + "-" + UUID.randomUUID());
		for (int i = 0; i < genreCount; i++) {
			Genre genre = genreRepository.save(GenreFixture.create("Genre" + SUFFIX + "-" + UUID.randomUUID()));
			createdProduct.addGenre(genre);
		}
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, 10));
		return product;
	}

	private LimitedDrop createScheduledDrop() throws Exception {
		String adminBearer = adminBearer();
		Product product = seedProduct(100);
		LimitedDropCreateRequest createRequest = new LimitedDropCreateRequest(product.getId(), 100, 2,
				LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
		MvcResult createResult = mockMvc.perform(post("/api/v1/admin/limited-drops")
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isCreated())
				.andReturn();
		Long dropId = objectMapper.readTree(createResult.getResponse().getContentAsString())
				.path("data").path("id").asLong();
		return limitedDropRepository.findById(dropId).orElseThrow();
	}
}
