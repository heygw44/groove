package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCancelService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentConfirmService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 토스 승인 호출이 끝나기 전에 주문이 취소되면(사용자/관리자 취소) 승인 반영이 거절되는데, 이때 즉시 보상
 * 취소가 도는지 검증한다. {@code paymentClient.confirm} 스텁의 응답 콜백 안에서 {@link OrderCancelService#cancel}
 * 을 직접 호출해 "토스 승인 호출 도중 주문이 먼저 취소됨"을 스레드 분기 없이 결정적으로 재현한다.
 */
@AutoConfigureMockMvc
class PaymentConfirmCompensationIntegrationTest extends IntegrationTestSupport {

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
	private AlbumRepository albumRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentConfirmService paymentConfirmService;

	@Autowired
	private OrderCancelService orderCancelService;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	@Nested
	@DisplayName("토스 승인 호출 중 주문이 취소되면")
	class OrderCanceledWhileTossConfirmInFlight {

		@Test
		@DisplayName("보상 취소가 성공하면 ORDER_EXPIRED 를 던지고 결제는 CANCELED, 재고는 한 번만 복구된다")
		void compensatesAndThrowsOrderExpiredWhenCancelSucceeds() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = "tviva-" + UUID.randomUUID();
			LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			LocalDateTime canceledAt = approvedAt.plusSeconds(1);
			given(paymentClient.confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any(BigDecimal.class)))
					.willAnswer(invocation -> {
						orderCancelService.cancel(member.getId(), orderInfo.orderId(), new OrderCancelRequest(null));
						return new PaymentConfirmResult(paymentKey, orderInfo.orderNumber(), "카드",
								orderInfo.finalAmount(), approvedAt);
					});
			given(paymentClient.cancel(eq(paymentKey), eq(PaymentCompensator.ORDER_INVALIDATED_REASON)))
					.willReturn(new PaymentCancelResult(paymentKey, "CANCELED", canceledAt));

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(confirmRequest(paymentKey,
									orderInfo.orderNumber(), orderInfo.finalAmount()))))
					.andExpect(status().isConflict());

			verify(paymentClient, times(1)).cancel(eq(paymentKey), eq(PaymentCompensator.ORDER_INVALIDATED_REASON));
			Payment payment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
			assertThat(payment.getCanceledAt()).isEqualTo(canceledAt);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			Stock stock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("보상 취소마저 결과 불명이면 결제는 UNKNOWN 으로 남고 PAYMENT_RESULT_UNKNOWN 을 던진다")
		void marksUnknownWhenCompensationCancelResultIsUnknown() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = "tviva-" + UUID.randomUUID();
			LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any(BigDecimal.class)))
					.willAnswer(invocation -> {
						orderCancelService.cancel(member.getId(), orderInfo.orderId(), new OrderCancelRequest(null));
						return new PaymentConfirmResult(paymentKey, orderInfo.orderNumber(), "카드",
								orderInfo.finalAmount(), approvedAt);
					});
			BusinessException resultUnknown = new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN,
					"TOSS 통신 실패: Read timed out");
			given(paymentClient.cancel(eq(paymentKey), eq(PaymentCompensator.ORDER_INVALIDATED_REASON)))
					.willThrow(resultUnknown);

			// when & then
			mockMvc.perform(post("/api/v1/payments/confirm")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(confirmRequest(paymentKey,
									orderInfo.orderNumber(), orderInfo.finalAmount()))))
					.andExpect(status().isServiceUnavailable());

			Payment payment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
		}
	}

	private record OrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount) {
	}

	private PaymentConfirmRequest confirmRequest(String paymentKey, String orderNumber, BigDecimal amount) {
		return new PaymentConfirmRequest(paymentKey, orderNumber, amount.longValueExact());
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
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.save(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Member signup() throws Exception {
		String email = "payment-confirm-comp-" + UUID.randomUUID() + "@groove.com";
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
