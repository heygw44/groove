package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCancelService;
import com.groove.order.service.OrderService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentWebhookService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 토스 웹훅이 재시도 상한을 넘겨 FAILED 로 확정된 결제를 재조회로 검증해 수렴시키는지 검증한다. 본문의
 * status 는 절대 판단에 쓰지 않는다는 것이 핵심이라, 매 시나리오에서 lookup() 목 응답만이 결과를 결정하게
 * 한다. 공유 DB 라 조회 단언은 항상 자기 행의 id/paymentKey 로만 한다.
 */
class PaymentWebhookIntegrationTest extends IntegrationTestSupport {

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

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderCancelService orderCancelService;

	@Autowired
	private PaymentWebhookService paymentWebhookService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	@Nested
	@DisplayName("handle()")
	class Handle {

		@Test
		@DisplayName("FAILED 결제·주문 PENDING 에 웹훅이 오고 재조회가 DONE 이면 결제를 DONE, 주문을 PAID 로 되돌린다")
		void resolvesFailedPaymentToDoneWhenLookupIsDone() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedFailedPayment(seeded.orderId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "webhook-key-1", "카드", seeded.finalAmount(), now(), null));

			// when
			paymentWebhookService.handle(webhookBody("webhook-key-1", seeded.orderNumber(), "DONE",
					"PAYMENT_STATUS_CHANGED", "2026-09-22T10:00:00+09:00"));

			// then
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.DONE);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("FAILED 결제·주문 CANCELED 에 웹훅이 오고 재조회가 DONE 이면 보상 취소로 결제를 CANCELED 로 수렴시킨다")
		void resolvesFailedPaymentToCanceledWhenOrderAlreadyCanceled() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			orderCancelService.cancel(seeded.memberId(), seeded.orderId(), new OrderCancelRequest(null));
			Payment payment = seedFailedPayment(seeded.orderId());
			LocalDateTime approvedAt = now().minusMinutes(5).truncatedTo(ChronoUnit.SECONDS);
			LocalDateTime canceledAt = now().truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "webhook-key-2", "카드", seeded.finalAmount(), approvedAt, null));
			given(paymentClient.cancel(eq("webhook-key-2"), eq(PaymentCompensator.ORDER_INVALIDATED_REASON)))
					.willReturn(new PaymentCancelResult("webhook-key-2", "CANCELED", canceledAt));

			// when
			paymentWebhookService.handle(webhookBody("webhook-key-2", seeded.orderNumber(), "DONE",
					"PAYMENT_STATUS_CHANGED", "2026-09-22T10:00:00+09:00"));

			// then
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
		}

		@Test
		@DisplayName("같은 이벤트가 두 번 오면 두 번째는 저장하지 않는다")
		void ignoresDuplicateEvent() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			seedFailedPayment(seeded.orderId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "webhook-key-3", "카드", seeded.finalAmount(), now(), null));
			String body = webhookBody("webhook-key-3", seeded.orderNumber(), "DONE", "PAYMENT_STATUS_CHANGED",
					"2026-09-22T11:00:00+09:00");

			// when
			paymentWebhookService.handle(body);
			paymentWebhookService.handle(body);

			// then
			Long count = jdbcTemplate.queryForObject(
					"select count(*) from payment_webhook_event where payment_key = ?", Long.class,
					"webhook-key-3");
			assertThat(count).isEqualTo(1L);
		}

		@Test
		@DisplayName("모르는 orderId 면 저장하지 않고 예외 없이 끝난다")
		void doesNotStoreEventForUnknownOrderId() {
			// given
			String unknownOrderId = "20260922-UNKNOWN1";
			String body = webhookBody("webhook-key-4", unknownOrderId, "DONE", "PAYMENT_STATUS_CHANGED",
					"2026-09-22T12:00:00+09:00");
			long before = countWebhookEvents();

			// when & then: 예외를 던지지 않는다.
			paymentWebhookService.handle(body);
			assertThat(countWebhookEvents()).isEqualTo(before);
		}

		@Test
		@DisplayName("본문이 JSON 으로 파싱되지 않으면 저장하지 않고 예외 없이 끝난다")
		void doesNotStoreEventForBrokenBody() {
			// given
			long before = countWebhookEvents();

			// when & then
			paymentWebhookService.handle("이건 JSON 이 아니다 {{{");
			assertThat(countWebhookEvents()).isEqualTo(before);
		}

		@Test
		@DisplayName("대상 이벤트가 아니면 저장하지 않고 예외 없이 끝난다")
		void doesNotStoreEventForOtherEventType() {
			// given
			String body = webhookBody("webhook-key-other", "20260922-OTHEREVT", "DONE", "METHOD_UPDATED",
					"2026-09-22T12:30:00+09:00");
			long before = countWebhookEvents();

			// when & then
			paymentWebhookService.handle(body);
			assertThat(countWebhookEvents()).isEqualTo(before);
		}

		@Test
		@DisplayName("data.orderId 가 없으면 저장하지 않고 예외 없이 끝난다")
		void doesNotStoreEventWhenOrderIdMissing() {
			// given
			String body = "{ \"eventType\": \"PAYMENT_STATUS_CHANGED\", \"createdAt\": "
					+ "\"2026-09-22T12:40:00+09:00\", \"data\": { \"paymentKey\": \"webhook-key-noorder\", "
					+ "\"status\": \"DONE\" } }";
			long before = countWebhookEvents();

			// when & then
			paymentWebhookService.handle(body);
			assertThat(countWebhookEvents()).isEqualTo(before);
		}

		@Test
		@DisplayName("재조회가 실패하면 503 을 던지고 ERROR 로 남기며, 재전송되면 같은 행을 재사용해 DONE 으로 수렴한다")
		void recoversOnRetryAfterLookupFailure() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedFailedPayment(seeded.orderId());
			String body = webhookBody("webhook-key-6", seeded.orderNumber(), "DONE", "PAYMENT_STATUS_CHANGED",
					"2026-09-22T14:00:00+09:00");
			willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, "TOSS 통신 실패"))
					.given(paymentClient).lookup(seeded.orderNumber());

			// when & then: 첫 시도는 재조회 실패로 503(BusinessException) 이다.
			assertThatThrownBy(() -> paymentWebhookService.handle(body))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PAYMENT_RESULT_UNKNOWN);
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.FAILED);
			String result = jdbcTemplate.queryForObject(
					"select result from payment_webhook_event where payment_key = ?", String.class,
					"webhook-key-6");
			assertThat(result).isEqualTo("ERROR");

			// when: 토스가 같은 이벤트를 재전송하고, 이번엔 재조회가 성공한다. given(mock.method()) 형태는 기존
			// willThrow 스텁이 남아 있는 채로 즉시 재평가돼 다시 던지므로, 평가 없이 스텁만 새로 얹는
			// willReturn(...).given(mock).method() 형태로 덮어써야 한다.
			willReturn(new PaymentLookupResult(PaymentLookupStatus.DONE, "webhook-key-6", "카드",
					seeded.finalAmount(), now(), null)).given(paymentClient).lookup(seeded.orderNumber());
			paymentWebhookService.handle(body);

			// then: ERROR 행을 재사용해 새 행을 만들지 않고 DONE 으로 수렴한다.
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.DONE);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PAID);
			Long count = jdbcTemplate.queryForObject(
					"select count(*) from payment_webhook_event where payment_key = ?", Long.class,
					"webhook-key-6");
			assertThat(count).isEqualTo(1L);
		}

		@Test
		@DisplayName("본문 status 가 DONE 이어도 재조회가 ABORTED 면 상태를 바꾸지 않는다")
		void doesNotChangeStatusWhenLookupDisagreesWithBody() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedFailedPayment(seeded.orderId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.ABORTED, null, null, null, null, null));

			// when: 본문은 DONE 이라고 위조돼 있다.
			paymentWebhookService.handle(webhookBody("webhook-key-5", seeded.orderNumber(), "DONE",
					"PAYMENT_STATUS_CHANGED", "2026-09-22T13:00:00+09:00"));

			// then
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.FAILED);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PENDING);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private long countWebhookEvents() {
		return jdbcTemplate.queryForObject("select count(*) from payment_webhook_event", Long.class);
	}

	private String webhookBody(String paymentKey, String tossOrderId, String status, String eventType,
			String createdAt) {
		return ("{ \"eventType\": \"%s\", \"createdAt\": \"%s\", \"data\": { \"paymentKey\": \"%s\", "
				+ "\"orderId\": \"%s\", \"status\": \"%s\" } }")
				.formatted(eventType, createdAt, paymentKey, tossOrderId, status);
	}

	private Payment seedFailedPayment(Long orderId) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		Payment payment = Payment.ready(order);
		payment.fail("대사 상한 초과");
		return paymentRepository.saveAndFlush(payment);
	}

	private SeededOrder seedPendingOrder(int stockQuantity, int purchaseQuantity) {
		Member member = memberRepository.save(MemberFixture.create("webhook-" + UUID.randomUUID() + "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, stockQuantity));
		OrderCreateResponse response = orderService.create(member.getId(),
				OrderFixture.directRequest(product.getId(), purchaseQuantity, address.getId()));
		return new SeededOrder(member.getId(), product, response.orderId(), response.orderNumber(),
				response.finalAmount());
	}

	private record SeededOrder(Long memberId, Product product, Long orderId, String orderNumber,
			BigDecimal finalAmount) {
	}
}
