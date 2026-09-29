package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.repository.CouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
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
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.AlbumRepository;
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
	AlbumRepository albumRepository;

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
			// 목록·상세는 결제 확정(placed_at) 전 주문을 숨긴다. 가상계좌 발급처럼 결제 전에도 확정될 수
			// 있는 상황을 흉내내 이 테스트에서는 결제 승인 없이 placed_at 만 채운다.
			placeOrder(orderId);

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
	@DisplayName("장바구니 기반 주문")
	class CartBasedOrder {

		@Test
		@DisplayName("장바구니 상품으로 주문하면 재고가 차감되지만 결제 전에는 장바구니가 그대로 남는다")
		void createsOrderFromCartAndKeepsCartUntilPayment() throws Exception {
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

			// then: 결제 확정 전이라 장바구니 상품이 그대로 남는다(삭제는 결제 승인 시점으로 옮김)
			mockMvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.items", hasSize(2)));

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
			// POST /orders 는 같은 회원의 미확정(placed_at 없는) PENDING 주문을 새 주문서 제출 전에
			// SUPERSEDED 로 푼다. 이 시나리오는 "이미 확정된 주문의 쿠폰은 재사용할 수 없다"를 보려는
			// 것이므로 결제 확정을 흉내내 이 주문을 미확정 해제 대상에서 뺀다.
			placeOrder(orderId);

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

	private void placeOrder(long orderId) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		order.place(LocalDateTime.now());
		orderRepository.save(order);
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
