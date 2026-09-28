package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;
import com.groove.order.entity.OrderStatus;
import com.groove.order.entity.ShippingAddress;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 주문서를 다시 제출할 때(POST /orders) 같은 회원이 쥐고 있던 이전 미확정 PENDING 주문을 SUPERSEDED 로
 * 풀어 재고를 돌려주는지 검증한다.
 */
@AutoConfigureMockMvc
class OrderDraftReleaseIntegrationTest extends IntegrationTestSupport {

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
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@MockitoBean
	private PaymentClient paymentClient;

	@Nested
	@DisplayName("이전 미확정 주문 해제")
	class ReleasesPreviousDraft {

		@Test
		@DisplayName("새 주문서를 제출하면 이전 PENDING 주문이 SUPERSEDED 로 풀리고 재고가 복원된다")
		void supersedesPreviousDraftAndRestoresStock() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			long firstOrderId = createOrder(accessToken, product.getId(), 3, address.getId());
			assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(2);

			// when: 같은 회원이 새 주문서를 제출한다
			long secondOrderId = createOrder(accessToken, product.getId(), 1, address.getId());

			// then: 이전 주문은 SUPERSEDED 로 풀리고 재고는 첫 주문 복원 뒤 둘째 주문만큼만 줄어든다
			Order firstOrder = orderRepository.findById(firstOrderId).orElseThrow();
			assertThat(firstOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(firstOrder.getCancelReason()).isEqualTo(Order.SUPERSEDED_CANCEL_REASON);
			Order secondOrder = orderRepository.findById(secondOrderId).orElseThrow();
			assertThat(secondOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(4);
		}

		@Test
		@DisplayName("결제가 READY 이면 이전 주문을 건드리지 않는다")
		void skipsWhenPaymentReady() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			long firstOrderId = createOrder(accessToken, product.getId(), 2, address.getId());
			Order firstOrder = orderRepository.findById(firstOrderId).orElseThrow();
			paymentRepository.save(Payment.ready(firstOrder));

			// when
			createOrder(accessToken, product.getId(), 1, address.getId());

			// then: READY 결제가 걸린 주문은 대사가 결론 낼 때까지 그대로 둔다
			Order stillPending = orderRepository.findById(firstOrderId).orElseThrow();
			assertThat(stillPending.getStatus()).isEqualTo(OrderStatus.PENDING);
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(2);
		}

		@Test
		@DisplayName("한정반 주문은 건드리지 않는다")
		void skipsLimitedOrder() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			Order limitedOrder = Order.create("20260928-LIMITED1", member, ShippingAddress.from(address),
					OrderSource.LIMITED, LocalDateTime.now());
			limitedOrder.addItem(product, 1);
			orderRepository.save(limitedOrder);

			// when
			createOrder(accessToken, product.getId(), 1, address.getId());

			// then: 당첨 자리를 보존해야 하는 한정반 주문은 풀지 않는다
			Order stillPending = orderRepository.findById(limitedOrder.getId()).orElseThrow();
			assertThat(stillPending.getStatus()).isEqualTo(OrderStatus.PENDING);
		}

		@Test
		@DisplayName("SUPERSEDED 로 풀린 주문에 뒤늦게 결제 승인이 오면 ORDER_INVALID_STATUS 를 반환한다")
		void rejectsLateConfirmAfterSupersede() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new OrderCreateRequest(null, product.getId(), 1, address.getId(), null))))
					.andExpect(status().isCreated())
					.andReturn();
			String firstOrderNumber = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderNumber").asText();
			long firstOrderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
					.path("data").path("orderId").asLong();

			// when: 새 주문서 제출로 첫 주문이 SUPERSEDED 된 뒤, 뒤늦게 도착한 결제 승인 요청을 처리한다
			createOrder(accessToken, product.getId(), 1, address.getId());
			Order supersededOrder = orderRepository.findById(firstOrderId).orElseThrow();

			// then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PaymentConfirmRequest(
									"tviva-" + UUID.randomUUID(), firstOrderNumber,
									supersededOrder.getFinalAmount().longValueExact()))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("ORDER_INVALID_STATUS")));
			verify(paymentClient, never()).confirm(any(), any(), any());
		}
	}

	private long createOrder(String accessToken, Long productId, int quantity, Long addressId) throws Exception {
		OrderCreateRequest request = new OrderCreateRequest(null, productId, quantity, addressId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("orderId").asLong();
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
		String email = "order-draft-release-" + UUID.randomUUID() + "@groove.com";
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
