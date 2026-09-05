package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentConfirmService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 같은 결제 키로 동시에 승인 요청이 들어와도 결제 1건, 주문 PAID 1건만 남는지 검증한다.
 * MockMvc 를 스레드마다 새로 태우는 대신 {@link PaymentConfirmService} 를 직접 호출해
 * 트랜잭션 경계 밖(토스 호출)과 안(락)의 경합만 결정적으로 재현한다.
 */
@AutoConfigureMockMvc
class PaymentConfirmConcurrencyIntegrationTest extends IntegrationTestSupport {

	private static final int THREAD_COUNT = 2;

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
	private PaymentConfirmService paymentConfirmService;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(THREAD_COUNT);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("같은 결제 키로 동시에 승인 요청하면")
	class ConcurrentConfirm {

		@Test
		@DisplayName("결제는 한 건만 DONE 으로 남고 주문은 PAID 로 정합성이 유지된다")
		void keepsSinglePaymentAndPaidOrderUnderConcurrentConfirm() throws Exception {
			// given
			Member member = signup();
			String accessToken = login(member.getEmail());
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = seedProduct(5);
			OrderInfo orderInfo = createOrder(accessToken, product.getId(), 1, address.getId());
			String paymentKey = "tviva-" + UUID.randomUUID();
			stubConfirmSuccess(paymentKey, orderInfo.orderNumber(), orderInfo.finalAmount());
			PaymentConfirmRequest request = new PaymentConfirmRequest(paymentKey, orderInfo.orderNumber(),
					orderInfo.finalAmount().longValueExact());

			CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<Throwable>> results = new ArrayList<>();
			AtomicInteger successCount = new AtomicInteger();

			// when
			for (int i = 0; i < THREAD_COUNT; i++) {
				AtomicReference<Throwable> result = new AtomicReference<>();
				results.add(result);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						paymentConfirmService.confirm(member.getId(), request);
						successCount.incrementAndGet();
					} catch (Throwable throwable) {
						result.set(throwable);
					}
				});
			}
			readyLatch.await();
			startLatch.countDown();
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then: 한쪽은 성공하고, 다른 한쪽은 성공(멱등 재조회)하거나 ORDER_ALREADY_PAID 로만 진다.
			// 같은 결제 키로 동시에 들어오면 prepare() 는 둘 다 통과하지만(주문 상태가 approve() 전까지 PENDING
			// 이라 락 경합이 없다), approve() 는 먼저 커밋한 쪽이 주문을 PAID 로 바꾸고 뒤이어 락을 잡은 쪽은
			// order.markPaid() 가 가드하는 ORDER_ALREADY_PAID 로 떨어진다(uk_payment_key 충돌은 나지 않는다 —
			// 두 요청이 같은 결제 행을 재사용해 같은 키를 다시 쓰는 것이라 유니크 제약에 걸리지 않는다).
			assertThat(finished).isTrue();
			assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
			results.stream()
					.map(AtomicReference::get)
					.filter(throwable -> throwable != null)
					.forEach(throwable -> assertThat(throwable)
							.isInstanceOf(BusinessException.class)
							.extracting("errorCode")
							.isEqualTo(ErrorCode.ORDER_ALREADY_PAID));

			Payment payment = paymentRepository.findByOrderId(orderInfo.orderId()).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(paymentRepository.findAll().stream()
					.filter(p -> p.getOrder().getId().equals(orderInfo.orderId()))
					.count()).isEqualTo(1);
			Order order = orderRepository.findById(orderInfo.orderId()).orElseThrow();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			// prepare() 락 → 토스 호출 → approve() 락 구조라, 두 스레드가 각자 prepare 를 통과하면 토스는
			// 두 번 불릴 수 있다. DB 정합성은 결제/주문 행이 하나뿐이라는 위 단언들이 보장하므로 허용한다.
			verify(paymentClient, atMost(2)).confirm(eq(paymentKey), eq(orderInfo.orderNumber()), any());
		}
	}

	private record OrderInfo(Long orderId, String orderNumber, BigDecimal finalAmount) {
	}

	private void stubConfirmSuccess(String paymentKey, String orderNumber, BigDecimal amount) {
		LocalDateTime approvedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
		given(paymentClient.confirm(eq(paymentKey), eq(orderNumber), any(BigDecimal.class)))
				.willReturn(new PaymentConfirmResult(paymentKey, orderNumber, "카드", amount, approvedAt));
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
		String email = "payment-confirm-cc-" + UUID.randomUUID() + "@groove.com";
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
