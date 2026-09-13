package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.groove.inventory.entity.Stock;
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
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.order.service.OrderCancelService;
import com.groove.order.service.OrderService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.scheduler.PaymentReconcileScheduler;
import com.groove.payment.service.PaymentCompensator;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 토스 조회 결과와 DB 결제 상태를 맞추는 대사 스케줄러, 그리고 만료 스케줄러와의 협조를 검증한다. 대사 후보
 * 조회는 전역이라 다른 테스트가 남긴 READY/UNKNOWN 결제도 잡히므로, 자기 결제의 updated_at 을 아주 과거로
 * 밀어 배치 앞쪽에 세우고 단언은 항상 자기 id/tossOrderId 로만 한다.
 */
class PaymentReconcileIntegrationTest extends IntegrationTestSupport {

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
	private PaymentReconcileLogRepository paymentReconcileLogRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderCancelService orderCancelService;

	@Autowired
	private OrderExpirationScheduler orderExpirationScheduler;

	@Autowired
	private PaymentReconcileScheduler paymentReconcileScheduler;

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
		@DisplayName("UNKNOWN 결제에 토스가 DONE(금액 일치) 이면 승인 반영하고 판매량을 갱신한다")
		void approvesUnknownPaymentWhenTossDoneAndAmountMatches() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.UNKNOWN, oldUpdatedAt());
			LocalDateTime approvedAt = now().minusMinutes(3).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "toss-key-approve", "카드", seeded.finalAmount(), approvedAt, null));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(reloadedPayment.getPaymentKey()).isEqualTo("toss-key-approve");
			Order reloadedOrder = orderRepository.findById(seeded.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
			Product reloadedProduct = productRepository.findById(seeded.product().getId()).orElseThrow();
			assertThat(reloadedProduct.getSoldQuantity()).isEqualTo(1L);
			assertThat(lastLogAction(payment.getId())).isEqualTo(PaymentReconcileAction.APPROVED);
		}

		@Test
		@DisplayName("만료가 아닌 취소로 주문이 CANCELED 됐고 토스가 DONE 이면 보상 취소하고 재고는 그대로 둔다")
		void compensatesWhenOrderCanceledWhileTossDone() throws Exception {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			orderCancelService.cancel(seeded.memberId(), seeded.orderId(), new OrderCancelRequest(null));
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			LocalDateTime approvedAt = now().minusMinutes(3).truncatedTo(ChronoUnit.SECONDS);
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "toss-key-compensate", "카드", seeded.finalAmount(), approvedAt, null));
			given(paymentClient.cancel(eq("toss-key-compensate"), eq(PaymentCompensator.ORDER_INVALIDATED_REASON)))
					.willReturn(new PaymentCancelResult("toss-key-compensate", "CANCELED", canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			verify(paymentClient, times(1)).cancel(eq("toss-key-compensate"),
					eq(PaymentCompensator.ORDER_INVALIDATED_REASON));
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(reloadedPayment.getApprovedAt()).isEqualTo(approvedAt);
			assertThat(reloadedPayment.getCanceledAt()).isEqualTo(canceledAt);
			Order reloadedOrder = orderRepository.findById(seeded.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			Stock stock = stockRepository.findByProductId(seeded.product().getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(5);
			assertThat(lastLogAction(payment.getId())).isEqualTo(PaymentReconcileAction.CANCELED);
		}

		@Test
		@DisplayName("토스가 DONE 이지만 금액이 다르면 상태는 그대로 두고 재시도 횟수만 올리며 MANUAL_REVIEW 로 남긴다")
		void marksManualReviewWhenAmountMismatch() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			BigDecimal mismatchedAmount = seeded.finalAmount().add(BigDecimal.TEN);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, "toss-key-mismatch", "카드", mismatchedAmount, now(), null));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(reloadedPayment.getReconcileAttempts()).isEqualTo(1);
			Order reloadedOrder = orderRepository.findById(seeded.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
			PaymentReconcileLog log = lastLog(payment.getId());
			assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
			assertThat(log.getTossStatus()).isEqualTo("DONE");
		}

		@Test
		@DisplayName("grace 안의 결제는 대사 후보가 아니다")
		void skipsPaymentWithinGrace() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, now().minusSeconds(30));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			verify(paymentClient, never()).lookup(eq(seeded.orderNumber()));
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.READY);
			assertThat(reloadedPayment.getReconcileAttempts()).isZero();
		}

		@Test
		@DisplayName("재시도 상한에 닿고 DONE 관측 이력이 없으면 결제를 FAILED 로 확정한다")
		void failsWhenMaxAttemptsReachedWithoutHistory() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			jdbcTemplate.update("update payment set reconcile_attempts = 9 where id = ?", payment.getId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(
					new PaymentLookupResult(PaymentLookupStatus.IN_PROGRESS, null, null, null, null, null));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(reloadedPayment.getReconcileAttempts()).isEqualTo(10);
			assertThat(lastLogAction(payment.getId())).isEqualTo(PaymentReconcileAction.FAILED);
		}

		@Test
		@DisplayName("두 인스턴스가 동시에 돌아도 같은 결제를 두 번 처리하지 않는다")
		void doesNotProcessSamePaymentTwiceUnderConcurrentRuns() throws Exception {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			LocalDateTime approvedAt = now().minusMinutes(3).truncatedTo(ChronoUnit.SECONDS);
			CountDownLatch startedLatch = new CountDownLatch(1);
			CountDownLatch releaseLatch = new CountDownLatch(1);
			given(paymentClient.lookup(seeded.orderNumber())).willAnswer(invocation -> {
				startedLatch.countDown();
				releaseLatch.await(10, TimeUnit.SECONDS);
				return new PaymentLookupResult(PaymentLookupStatus.DONE, "toss-key-concurrent", "카드",
						seeded.finalAmount(), approvedAt, null);
			});

			// when: 첫 스레드가 락을 쥐고 조회를 붙잡고 있는 동안 두 번째 스레드는 락 획득에 실패해야 한다.
			ExecutorService executorService = Executors.newFixedThreadPool(2);
			try {
				Future<?> first = executorService.submit(() -> paymentReconcileScheduler.reconcile());
				assertThat(startedLatch.await(10, TimeUnit.SECONDS)).isTrue();
				Future<?> second = executorService.submit(() -> paymentReconcileScheduler.reconcile());
				second.get(10, TimeUnit.SECONDS);
				releaseLatch.countDown();
				first.get(10, TimeUnit.SECONDS);
			} finally {
				executorService.shutdownNow();
			}

			// then
			verify(paymentClient, times(1)).lookup(eq(seeded.orderNumber()));
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(reloadedPayment.getPaymentKey()).isEqualTo("toss-key-concurrent");
		}
	}

	@Nested
	@DisplayName("reconcile() 와 만료 스케줄러 협조")
	class ReconcileWithExpiration {

		@Test
		@DisplayName("READY 결제에 토스 조회가 없으면 FAILED 로 확정하고 다음 만료 주기에 재고가 복구된다")
		void failsThenExpiresAndRestoresStock() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			expireOrderNow(seeded.orderId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(PaymentLookupResult.notFound());

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
			assertThat(lastLogAction(payment.getId())).isEqualTo(PaymentReconcileAction.FAILED);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order reloadedOrder = orderRepository.findById(seeded.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
			Stock stock = stockRepository.findByProductId(seeded.product().getId()).orElseThrow();
			assertThat(stock.getQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("READY/UNKNOWN 결제가 걸린 기한 지난 주문은 건너뛰고, 결제 없는 기한 지난 주문은 만료한다")
		void skipsOrderWithUnresolvedPaymentButExpiresOrderWithoutPayment() {
			// given
			SeededOrder withPayment = seedPendingOrder(5, 1);
			seedPayment(withPayment.orderId(), PaymentStatus.READY, oldUpdatedAt());
			expireOrderNow(withPayment.orderId());
			SeededOrder withoutPayment = seedPendingOrder(5, 1);
			expireOrderNow(withoutPayment.orderId());

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order skipped = orderRepository.findById(withPayment.orderId()).orElseThrow();
			assertThat(skipped.getStatus()).isEqualTo(OrderStatus.PENDING);
			Stock skippedStock = stockRepository.findByProductId(withPayment.product().getId()).orElseThrow();
			assertThat(skippedStock.getQuantity()).isEqualTo(4);

			Order expired = orderRepository.findById(withoutPayment.orderId()).orElseThrow();
			assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELED);
			Stock expiredStock = stockRepository.findByProductId(withoutPayment.product().getId()).orElseThrow();
			assertThat(expiredStock.getQuantity()).isEqualTo(5);
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private LocalDateTime oldUpdatedAt() {
		return now().minusDays(30).truncatedTo(ChronoUnit.SECONDS);
	}

	private PaymentReconcileAction lastLogAction(Long paymentId) {
		return lastLog(paymentId).getAction();
	}

	private PaymentReconcileLog lastLog(Long paymentId) {
		List<PaymentReconcileLog> logs = paymentReconcileLogRepository.findByPaymentIdOrderByIdAsc(paymentId);
		assertThat(logs).isNotEmpty();
		return logs.get(logs.size() - 1);
	}

	private void expireOrderNow(Long orderId) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		OrderFixture.withExpiresAt(order, now().minusMinutes(1));
		orderRepository.saveAndFlush(order);
	}

	private Payment seedPayment(Long orderId, PaymentStatus status, LocalDateTime updatedAt) {
		Order order = orderRepository.findById(orderId).orElseThrow();
		Payment payment = Payment.ready(order);
		if (status == PaymentStatus.UNKNOWN) {
			payment.markUnknown("확인 중");
		}
		Payment saved = paymentRepository.saveAndFlush(payment);
		jdbcTemplate.update("update payment set updated_at = ? where id = ?", Timestamp.valueOf(updatedAt),
				saved.getId());
		return paymentRepository.findById(saved.getId()).orElseThrow();
	}

	private SeededOrder seedPendingOrder(int stockQuantity, int purchaseQuantity) {
		Member member = memberRepository.save(MemberFixture.create("recon-" + UUID.randomUUID() + "@groove.com"));
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
