package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditLog;
import com.groove.admin.repository.AdminAuditLogRepository;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.repository.CouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.entity.StockHistory;
import com.groove.inventory.repository.StockHistoryRepository;
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
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class OrderFlowIntegrationTest extends IntegrationTestSupport {

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
	StockRepository stockRepository;

	@Autowired
	StockHistoryRepository stockHistoryRepository;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	CouponRepository couponRepository;

	@Autowired
	AdminAuditLogRepository adminAuditLogRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Nested
	@DisplayName("생성 → 목록/상세 조회 → 취소 흐름")
	class OrderFlow {

		@Test
		@DisplayName("주문을 생성하고 취소하면 재고와 이력이 각 단계에서 일관되게 반영된다")
		void createsAndCancelsOrderConsistently() throws Exception {
			// given: 회원, 배송지, 재고 5개인 상품을 준비한다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);

			// when: 상품 5개를 전량 주문한다
			OrderCreateRequest createRequest = new OrderCreateRequest(null, product.getId(), 5, address.getId(),
					null);
			MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderId").asLong();

			// then: 재고가 전량 소진되고 상품은 품절 상태가 된다
			Stock stockAfterCreate = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stockAfterCreate.getQuantity()).isZero();
			Product productAfterCreate = productRepository.findById(product.getId()).orElseThrow();
			assertThat(productAfterCreate.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stockAfterCreate.getId()))
					.hasSize(1);

			// when & then: 목록과 상세 조회 결과가 생성한 주문과 일치한다
			mockMvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is((int) orderId)))
					.andExpect(jsonPath("$.data.content[0].itemCount", is(1)));

			mockMvc.perform(get("/api/v1/orders/" + orderId)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items[0].quantity", is(5)));

			// when: 주문을 취소한다
			mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then: 재고가 복구되고 상품은 판매중으로 돌아오며 이력이 2건(OUT, CANCEL) 쌓인다
			Stock stockAfterCancel = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stockAfterCancel.getQuantity()).isEqualTo(5);
			Product productAfterCancel = productRepository.findById(product.getId()).orElseThrow();
			assertThat(productAfterCancel.getStatus()).isEqualTo(ProductStatus.ON_SALE);
			List<StockHistory> histories =
					stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stockAfterCancel.getId());
			assertThat(histories).hasSize(2);
			StockHistory cancelHistory = histories.get(1);
			assertThat(cancelHistory.getChangeType()).isEqualTo(StockChangeType.CANCEL);
			assertThat(cancelHistory.getQuantityDelta()).isEqualTo(5);
		}
	}

	@Nested
	@DisplayName("관리자 주문 상태 전이")
	class AdminStatusChange {

		@Test
		@DisplayName("PAID→PREPARING→SHIPPED→DELIVERED 는 순서대로 성공하고 DELIVERED→CANCELED 는 거부된다")
		void transitionsThroughAllowedStatusesAndRejectsInvalidTransition() throws Exception {
			// given: 결제 완료(PAID) 상태의 주문과 관리자 토큰을 준비한다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderCreateRequest createRequest = new OrderCreateRequest(null, product.getId(), 1, address.getId(),
					null);
			MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andReturn();
			long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderId").asLong();
			Order order = orderRepository.findById(orderId).orElseThrow();
			order.markPaid();
			orderRepository.save(order);

			Member admin = memberRepository.save(
					MemberFixture.createAdmin("order-flow-admin-" + UUID.randomUUID() + "@groove.com"));
			String adminBearer = "Bearer " + jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);

			// when & then: 허용된 전이는 순서대로 200 을 반환한다
			changeStatus(orderId, adminBearer, OrderStatus.PREPARING).andExpect(status().isOk());
			changeStatus(orderId, adminBearer, OrderStatus.SHIPPED).andExpect(status().isOk());
			changeStatus(orderId, adminBearer, OrderStatus.DELIVERED).andExpect(status().isOk());

			// when & then: DELIVERED→CANCELED 는 허용되지 않는 전이라 400 을 반환한다
			changeStatus(orderId, adminBearer, OrderStatus.CANCELED)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_INVALID_STATUS_TRANSITION")));

			// then: 감사 로그가 전이 횟수만큼 쌓인다
			List<AdminAuditLog> logs = adminAuditLogRepository.findAllByAdminIdOrderByIdAsc(admin.getId());
			assertThat(logs).extracting(AdminAuditLog::getAction).containsExactly(
					AdminAuditAction.ORDER_STATUS_CHANGE, AdminAuditAction.ORDER_STATUS_CHANGE,
					AdminAuditAction.ORDER_STATUS_CHANGE);
		}

		private ResultActions changeStatus(long orderId, String adminBearer, OrderStatus status) throws Exception {
			AdminOrderStatusChangeRequest request = new AdminOrderStatusChangeRequest(status);
			return mockMvc.perform(patch("/api/v1/admin/orders/" + orderId + "/status")
					.header(HttpHeaders.AUTHORIZATION, adminBearer)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)));
		}
	}

	@Nested
	@DisplayName("장바구니 기반 주문")
	class CartBasedOrder {

		@Test
		@DisplayName("장바구니 상품으로 주문하면 장바구니가 비워지고 각 상품 재고가 차감된다")
		void createsOrderFromCartAndClearsCart() throws Exception {
			// given: 회원, 배송지, 장바구니에 담은 두 상품을 준비한다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product firstProduct = seedProduct(5);
			Product secondProduct = seedProduct(3);
			int firstQuantity = 2;
			int secondQuantity = 1;

			long firstCartItemId = addToCart(accessToken, firstProduct.getId(), firstQuantity);
			long secondCartItemId = addToCart(accessToken, secondProduct.getId(), secondQuantity);

			// when: 장바구니 항목으로 주문을 생성한다
			OrderCreateRequest createRequest = OrderFixture.cartRequest(
					List.of(firstCartItemId, secondCartItemId), address.getId());
			mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated());

			// then: 장바구니가 비워진다
			mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items", hasSize(0)));

			// then: 두 상품의 재고가 각각 주문 수량만큼 차감되고 OUT 이력이 남는다
			assertStockDeductedByOut(firstProduct.getId(), 5, firstQuantity);
			assertStockDeductedByOut(secondProduct.getId(), 3, secondQuantity);
		}

		private long addToCart(String accessToken, Long productId, int quantity) throws Exception {
			MvcResult result = mockMvc.perform(post("/api/v1/cart/items")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(CartFixture.addRequest(productId, quantity))))
					.andExpect(status().isCreated())
					.andReturn();
			return objectMapper.readTree(result.getResponse().getContentAsString())
					.path("data").path("id").asLong();
		}

		private void assertStockDeductedByOut(Long productId, int initialQuantity, int orderedQuantity) {
			Stock stock = stockRepository.findByProductId(productId).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(initialQuantity - orderedQuantity);
			List<StockHistory> histories =
					stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stock.getId());
			assertThat(histories).hasSize(1);
			assertThat(histories.get(0).getChangeType()).isEqualTo(StockChangeType.OUT);
			assertThat(histories.get(0).getQuantityDelta()).isEqualTo(-orderedQuantity);
		}
	}

	@Nested
	@DisplayName("쿠폰을 적용한 주문")
	class CouponAppliedOrder {

		@Test
		@DisplayName("발급 → 쿠폰 적용 주문 → 재사용 거부 → 취소 → 재사용 순으로 진행된다")
		void appliesUsesCancelsAndReusesCoupon() throws Exception {
			// given: 회원, 배송지, 재고 5개인 상품, 5천원 정액 쿠폰을 준비한다
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			String code = "FLOW" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
			couponRepository.save(Coupon.create(code, "가을맞이 5천원 할인", DiscountType.FIXED, new BigDecimal("5000"),
					BigDecimal.ZERO, null, null, LocalDateTime.now().plusDays(7)));

			// when: 쿠폰을 발급받는다
			MvcResult issueResult = mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CouponIssueRequest(code))))
					.andExpect(status().isCreated())
					.andReturn();
			long memberCouponId = objectMapper.readTree(issueResult.getResponse().getContentAsString())
					.path("data").path("memberCouponId").asLong();

			// when & then: 쿠폰을 적용해 주문하면 할인 금액과 쿠폰명이 반영된다
			OrderCreateRequest createRequest = OrderFixture.directRequestWithCoupon(product.getId(), 1,
					address.getId(), memberCouponId);
			MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.discountAmount", is(5000.0)))
					.andExpect(jsonPath("$.data.finalAmount", is(40000.0)))
					.andExpect(jsonPath("$.data.couponName", is("가을맞이 5천원 할인")))
					.andReturn();
			long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderId").asLong();

			// then: 재고는 주문한 만큼만 차감된다
			Stock stockAfterFirstOrder = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stockAfterFirstOrder.getQuantity()).isEqualTo(4);

			// when & then: 이미 사용한 쿠폰으로 다시 주문하면 409 를 반환하고 재고는 그대로다
			mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COUPON_ALREADY_USED")));
			Stock stockAfterRejectedReuse = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stockAfterRejectedReuse.getQuantity()).isEqualTo(4);

			// when: 첫 주문을 취소한다
			mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CANCELED")));

			// then: 취소로 복구된 쿠폰을 다시 적용해 주문할 수 있다
			mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(createRequest)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.discountAmount", is(5000.0)));
		}
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member signup() throws Exception {
		String email = "order-flow-" + UUID.randomUUID() + "@groove.com";
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
