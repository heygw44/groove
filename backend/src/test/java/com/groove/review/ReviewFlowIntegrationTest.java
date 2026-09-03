package com.groove.review;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;

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
import com.groove.order.dto.AdminOrderStatusChangeRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
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
			// given: 구매자가 상품을 주문하고 DELIVERED 까지 전이한다
			Member buyer = signup();
			String buyerToken = login(buyer.getEmail());
			Address address = addressRepository.save(AddressFixture.create(buyer));
			Product product = seedProduct(5);
			long orderId = createOrder(buyerToken, product.getId(), address.getId());
			deliverOrder(orderId);

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
		Order order = orderRepository.findById(orderId).orElseThrow();
		order.markPaid();
		orderRepository.save(order);

		Member admin = memberRepository.save(
				MemberFixture.createAdmin("review-flow-admin-" + UUID.randomUUID() + "@groove.com"));
		String adminBearer = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

		changeStatus(orderId, adminBearer, OrderStatus.PREPARING).andExpect(status().isOk());
		changeStatus(orderId, adminBearer, OrderStatus.SHIPPED).andExpect(status().isOk());
		changeStatus(orderId, adminBearer, OrderStatus.DELIVERED).andExpect(status().isOk());
	}

	private ResultActions changeStatus(long orderId, String adminBearer, OrderStatus status) throws Exception {
		AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(status);
		return mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
				.header(HttpHeaders.AUTHORIZATION, adminBearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)));
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
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
