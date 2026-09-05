package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.MockServerRestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * {@code tossRestClient} 가 실제로는 {@link MockRestServiceServer} 로 향하는지, 즉 프로덕션
 * 요청 경로 그대로(컨트롤러 → 서비스 → RestClient) 승인이 끝까지 이어지는지를 검증한다.
 * {@link PaymentFlowIntegrationTest} 는 {@code PaymentClient} 자체를 목으로 바꿔치기하므로
 * HTTP 전송 계층은 별도로 이 클래스에서 덮는다.
 */
@AutoConfigureMockMvc
@Import(PaymentConfirmIntegrationTest.MockTossConfig.class)
class PaymentConfirmIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private MockServerRestClientCustomizer mockServerRestClientCustomizer;

	private MockRestServiceServer server;

	@BeforeEach
	void setUpServer() {
		server = mockServerRestClientCustomizer.getServer();
	}

	@AfterEach
	void tearDown() {
		server.verify();
		server.reset();
	}

	@Test
	@DisplayName("승인 요청이 실제로 토스 RestClient 를 타고 나가면 결제가 DONE, 주문이 PAID 로 바뀐다")
	void confirmsThroughRealRestClientAndMarksOrderPaid() throws Exception {
		// given
		Member member = signup();
		String accessToken = login(member.getEmail());
		Address address = addressRepository.save(AddressFixture.create(member));
		Product product = seedProduct(5);
		OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
		String paymentKey = uniquePaymentKey();

		server.expect(requestTo(endsWith("/v1/payments/confirm")))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(
						tossConfirmResponse(paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount()),
						MediaType.APPLICATION_JSON));

		// when & then
		mockMvc.perform(post("/api/v1/payments/confirm")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								confirmRequest(paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status", is("DONE")));

		Payment payment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
		Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
		assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Nested
	@DisplayName("승인 거부")
	class Rejections {

		@ParameterizedTest(name = "요청 금액이 실제 금액과 {0}원 차이 나면 토스를 호출하지 않고 거부한다")
		@ValueSource(longs = { -1000L, 1000L })
		@DisplayName("금액이 위변조되면 토스를 호출하지 않고 ORDER_AMOUNT_MISMATCH 로 거부한다")
		void rejectsTamperedAmount(long diff) throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			long tamperedAmount = orderInfo.finalAmount().longValueExact() + diff;

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(uniquePaymentKey(), orderInfo.orderNumber(), tamperedAmount))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("ORDER_AMOUNT_MISMATCH")));

			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(paymentRepository.findByOrderId(orderInfo.orderId())).isEmpty();
		}

		@Test
		@DisplayName("존재하지 않는 주문이면 ORDER_NOT_FOUND 로 거부한다")
		void rejectsNonexistentOrder() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(uniquePaymentKey(), "20260101-NOSUCH01", 1000L))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));
		}

		@Test
		@DisplayName("다른 회원의 주문이면 ORDER_NOT_FOUND 로 거부한다")
		void rejectsOtherMembersOrder() throws Exception {
			// given
			Member owner = signup();
			String ownerToken = login(owner.getEmail());
			Address address = addressRepository.save(AddressFixture.create(owner));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(ownerToken, product.getId(), 1, address.getId());
			Member intruder = signup();
			String intruderToken = login(intruder.getEmail());

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(uniquePaymentKey(), orderInfo.orderNumber(),
											orderInfo.finalAmount()))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("ORDER_NOT_FOUND")));

			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		}

		@Test
		@DisplayName("이미 다른 결제 키로 승인된 주문이면 PAYMENT_ALREADY_DONE 으로 거부한다")
		void rejectsAlreadyDoneOrderWithDifferentKey() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String approvedKey = uniquePaymentKey();
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			OrderFixture.markPaid(order);
			orderRepository.saveAndFlush(order);
			paymentRepository.saveAndFlush(PaymentFixture.approved(order, approvedKey));

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(uniquePaymentKey(), orderInfo.orderNumber(),
											orderInfo.finalAmount()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("PAYMENT_ALREADY_DONE")));
		}

		@Test
		@DisplayName("결제 기한이 지난 주문이면 토스를 호출하지 않고 ORDER_EXPIRED 로 거부한다")
		void rejectsExpiredOrder() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, LocalDateTime.now().minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									confirmRequest(uniquePaymentKey(), orderInfo.orderNumber(),
											orderInfo.finalAmount()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_EXPIRED")));

			Order reloaded = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
		}
	}

	@TestConfiguration
	static class MockTossConfig {

		@Bean
		MockServerRestClientCustomizer mockServerRestClientCustomizer() {
			return new MockServerRestClientCustomizer();
		}
	}

	private record OrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount) {
	}

	private String uniquePaymentKey() {
		return "tviva-" + UUID.randomUUID();
	}

	private PaymentConfirmRequest confirmRequest(String paymentKey, String orderNumber, long amount) {
		return new PaymentConfirmRequest(paymentKey, orderNumber, amount);
	}

	private PaymentConfirmRequest confirmRequest(String paymentKey, String orderNumber, BigDecimal amount) {
		return confirmRequest(paymentKey, orderNumber, amount.longValueExact());
	}

	private String tossConfirmResponse(String paymentKey, String orderNumber, BigDecimal amount) {
		return """
				{
					"paymentKey": "%s",
					"orderId": "%s",
					"status": "DONE",
					"method": "카드",
					"totalAmount": %s,
					"approvedAt": "2026-09-02T10:01:12+09:00",
					"cancels": null
				}
				""".formatted(paymentKey, orderNumber, amount.longValueExact());
	}

	private OrderInfo createOrder(String accessToken, Long productId, int quantity, Long addressId)
			throws Exception {
		OrderCreateRequest createRequest = new OrderCreateRequest(null, productId, quantity, addressId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(createRequest)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
		return new OrderInfo(data.path("orderId").asLong(), data.path("orderNumber").asText(),
				new BigDecimal(data.path("finalAmount").asText()));
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member signup() throws Exception {
		String email = "payment-confirm-" + UUID.randomUUID() + "@groove.com";
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
