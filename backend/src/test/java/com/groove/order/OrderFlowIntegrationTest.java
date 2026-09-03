package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
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
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(stockAfterCancel.getId()))
					.hasSize(2);
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
