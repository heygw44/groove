package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.client.dto.PaymentTransaction;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentSettlementReport;
import com.groove.payment.service.PaymentSettlementService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * PaymentSettlementService 가 실제 트랜잭션 경계(짧은 쓰기 트랜잭션 + 트랜잭션 밖 HTTP 조회)를 거쳐 결제를
 * 수렴시키는지 검증한다. 시드 방식은 {@code PaymentWebhookIntegrationTest} 를 따른다 — 공유 DB 라 조회
 * 단언은 항상 자기 행의 id 로만 한다.
 */
class PaymentSettlementIntegrationTest extends IntegrationTestSupport {

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
	private PaymentSettlementService settlementService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	@Nested
	@DisplayName("reconcile()")
	class Reconcile {

		@Test
		@DisplayName("FAILED 결제·주문 PENDING 상태에서 거래 조회가 DONE 이면 결제를 DONE, 주문을 PAID 로 되돌리고 "
				+ "settlement 로그를 남긴다")
		void resolvesFailedPaymentToDoneAndLogsSettlementDetail() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedFailedPayment(seeded.orderId());
			LocalDateTime from = now().minusHours(1);
			LocalDateTime to = now().plusHours(1);
			PaymentTransaction transaction = new PaymentTransaction("txn-settlement-1", "settlement-key-1",
					seeded.orderNumber(), "DONE", now());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "settlement-key-1", "카드", seeded.finalAmount(), now(), null));

			// when
			PaymentSettlementReport report = settlementService.reconcile(List.of(transaction), from, to);

			// then
			assertThat(report.applied()).isEqualTo(1);
			assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.DONE);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PAID);
			String detail = jdbcTemplate.queryForObject(
					"select detail from payment_reconcile_log where payment_id = ?", String.class, payment.getId());
			assertThat(detail).isEqualTo("settlement");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private Payment seedFailedPayment(Long orderId) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		Payment payment = Payment.ready(order);
		payment.fail("대사 상한 초과");
		return paymentRepository.saveAndFlush(payment);
	}

	private SeededOrder seedPendingOrder(int stockQuantity, int purchaseQuantity) {
		Member member = memberRepository.save(MemberFixture.create("settlement-" + UUID.randomUUID() + "@groove.com"));
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
