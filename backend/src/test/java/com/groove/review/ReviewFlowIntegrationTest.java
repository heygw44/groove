package com.groove.review;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemDeliverRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.dto.ReviewCreateRequest;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class ReviewFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

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
	OrderRepository orderRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	StockRepository stockRepository;

	@Nested
	@DisplayName("구매 확정 → 작성 → 목록 → 중복/미구매/타인수정 거부 흐름")
	class ReviewFlow {

		@Test
		@DisplayName("전체 흐름을 정상적으로 완료한다")
		void completesFullReviewFlow() throws Exception {
			// given: 구매자가 상품을 주문하고 DELIVERED 까지 전이한 뒤 구매확정까지 마친다(리뷰는 PURCHASE_CONFIRMED 부터)
			Member buyer = signup();
			String buyerToken = login(buyer.getEmail());
			Address address = addressRepository.save(AddressFixture.create(buyer));
			Product product = seedProduct(5);
			long orderId = createOrder(buyerToken, product.getId(), address.getId());
			deliverOrder(orderId);
			confirmPurchase(orderId, buyerToken);

			// when & then: 리뷰를 작성하면 201 을 반환한다
			MvcResult createResult = mockMvc.perform(post("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(ReviewFixture.createRequest())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.mine", is(true)))
					.andReturn();
			long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// then: 상품 상세의 평점 집계가 방금 작성한 리뷰로 갱신된다
			mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.averageRating", is(5.0)))
					.andExpect(jsonPath("$.data.reviewCount", is(1)));

			// then: 목록 조회에서 mine 이 true 로 보인다
			mockMvc.perform(get("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].mine", is(true)));

			// when & then: 같은 상품에 다시 작성하면 409 를 반환한다
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(ReviewFixture.createRequest())))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("REVIEW_ALREADY_EXISTS")));

			// when & then: 구매하지 않은 회원이 작성하면 403 을 반환한다
			Member other = signup();
			String otherToken = login(other.getEmail());
			mockMvc.perform(post("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(ReviewFixture.createRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("REVIEW_PURCHASE_REQUIRED")));

			// when & then: 타인이 리뷰를 수정하면 404 를 반환한다
			mockMvc.perform(patch("/api/v1/reviews/{id}", reviewId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewUpdateRequest(3, "수정", "수정 시도"))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("REVIEW_NOT_FOUND")));

			// when & then: 작성자 본인이 리뷰를 삭제하면 평점 집계가 초기화된다
			mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
					.andExpect(status().isOk());

			mockMvc.perform(get("/api/v1/products/{id}", product.getId()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.averageRating").doesNotExist())
					.andExpect(jsonPath("$.data.reviewCount", is(0)));
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("리뷰를 수정하면 응답의 수정 시각이 이후 목록 조회의 수정 시각과 같다")
		void returnsPersistedUpdatedAt() throws Exception {
			// given
			Member buyer = signup();
			String buyerToken = login(buyer.getEmail());
			Address address = addressRepository.save(AddressFixture.create(buyer));
			Product product = seedProduct(5);
			long orderId = createOrder(buyerToken, product.getId(), address.getId());
			deliverOrder(orderId);
			confirmPurchase(orderId, buyerToken);
			MvcResult createResult = mockMvc.perform(post("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(ReviewFixture.createRequest())))
					.andExpect(status().isCreated())
					.andReturn();
			long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// when
			MvcResult updateResult = mockMvc.perform(patch("/api/v1/reviews/{id}", reviewId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new ReviewUpdateRequest(3, "수정", "수정한 내용"))))
					.andExpect(status().isOk())
					.andReturn();
			String updatedAt = objectMapper.readTree(updateResult.getResponse().getContentAsString())
					.path("data").path("updatedAt").asText();

			// then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id").value(reviewId))
					.andExpect(jsonPath("$.data.content[0].updatedAt", is(updatedAt)));
		}
	}

	@Nested
	@DisplayName("checkEligibility()")
	class CheckEligibility {

		@Test
		@DisplayName("배송완료 상품주문에 반품을 요청하면 PURCHASE_CONFIRM_REQUIRED 대신 PURCHASE_REQUIRED 를 반환한다")
		void returnsPurchaseRequiredWhenReturnRequested() throws Exception {
			// given
			Member buyer = signup();
			String buyerToken = login(buyer.getEmail());
			Address address = addressRepository.save(AddressFixture.create(buyer));
			Product product = seedProduct(5);
			long orderId = createOrder(buyerToken, product.getId(), address.getId());
			deliverOrder(orderId);
			long itemId = orderRepository.findWithItemsById(orderId).orElseThrow().getItems().get(0).getId();
			mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/return", orderId, itemId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new OrderReturnRequest("사이즈가 안 맞음"))))
					.andExpect(status().isOk());

			// when & then
			mockMvc.perform(get("/api/v1/products/{productId}/reviews/eligibility", product.getId())
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.eligible", is(false)))
					.andExpect(jsonPath("$.data.reason", is("PURCHASE_REQUIRED")));
		}
	}

	private long createOrder(String accessToken, Long productId, Long addressId) throws Exception {
		OrderCreateRequest createRequest = new OrderCreateRequest(null, productId, 1, addressId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.path("data").path("orderId").asLong();
	}

	private void deliverOrder(long orderId) throws Exception {
		// markPaid() 는 상품주문(items)도 같이 옮기므로 findWithItemsById 로 지연 로딩 없이 가져온다
		Order order = orderRepository.findWithItemsById(orderId).orElseThrow();
		order.markPaid();
		order.place(LocalDateTime.now());
		orderRepository.save(order);
		long itemId = order.getItems().get(0).getId();

		Member admin = memberRepository.save(
				MemberFixture.createAdmin("review-flow-admin-" + UUID.randomUUID() + "@groove.com"));
		String adminBearer = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

		// 배송 진행은 상품주문 단위라 발주확인 → 발송처리 → 배송완료 일괄 처리 API 로 옮긴다
		performAdminItemAction(adminBearer, "confirm", new AdminOrderItemConfirmRequest(List.of(itemId)));
		performAdminItemAction(adminBearer, "ship", new AdminOrderItemShipRequest(
				List.of(new AdminOrderItemShipRequest.ShipItem(itemId, "CJ", "1234567890"))));
		performAdminItemAction(adminBearer, "deliver", new AdminOrderItemDeliverRequest(List.of(itemId)));
	}

	private void confirmPurchase(long orderId, String buyerToken) throws Exception {
		Order order = orderRepository.findWithItemsById(orderId).orElseThrow();
		long itemId = order.getItems().get(0).getId();
		mockMvc.perform(post("/api/v1/orders/{orderId}/items/{itemId}/confirm", orderId, itemId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
				.andExpect(status().isOk());
	}

	private void performAdminItemAction(String adminBearer, String action, Object request) throws Exception {
		mockMvc.perform(post("/api/v1/admin/order-items/" + action)
						.header(HttpHeaders.AUTHORIZATION, adminBearer)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.processed", is(1)));
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member signup() throws Exception {
		String email = "review-flow-" + UUID.randomUUID() + "@groove.com";
		mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new SignupRequest(email, "password1", "그루버"))))
				.andExpect(status().isCreated());
		return memberRepository.findByEmail(email).orElseThrow();
	}

	private String login(String email) throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest(email, "password1"))))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.path("data").path("accessToken").asText();
	}
}
