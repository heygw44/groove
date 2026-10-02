package com.groove.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.argThat;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.repository.LimitedPurchaseRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
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
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.client.dto.PaymentLookupStatus;
import com.groove.payment.client.dto.VirtualAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentReconcileAction;
import com.groove.payment.entity.PaymentReconcileLog;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentReconcileLogRepository;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.scheduler.PaymentReconcileScheduler;
import com.groove.payment.service.LimitedVirtualAccountCloser;
import com.groove.payment.service.PaymentCancelWriter;
import com.groove.payment.service.PaymentCompensator;
import com.groove.payment.service.PaymentRefundRequest;
import com.groove.payment.service.PaymentRefundWriter;
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
	private PaymentCancelWriter paymentCancelWriter;

	@Autowired
	private PaymentRefundWriter paymentRefundWriter;

	@Autowired
	private PaymentCancelRepository paymentCancelRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private StringRedisTemplate redisTemplate;

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
		@DisplayName("UNKNOWN 결제에 토스가 입금대기(가상계좌 정보 있음) 이면 입금대기로 반영하고 만료를 입금기한까지 늘린다")
		void issuesVirtualAccountForUnknownPaymentWhenTossWaitingForDeposit() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.UNKNOWN, oldUpdatedAt());
			LocalDateTime dueDate = now().plusDays(3).truncatedTo(ChronoUnit.SECONDS);
			VirtualAccountInfo virtualAccount = new VirtualAccountInfo("088", "12345678901234", "홍길동", dueDate,
					"secret-recon");
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.WAITING_FOR_DEPOSIT, "toss-key-va", "가상계좌", seeded.finalAmount(), null, null,
					null, null, null, virtualAccount));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.WAITING_FOR_DEPOSIT);
			assertThat(reloadedPayment.getPaymentKey()).isEqualTo("toss-key-va");
			assertThat(reloadedPayment.getVaAccountNumber()).isEqualTo("12345678901234");
			assertThat(reloadedPayment.getVaDueDate()).isEqualTo(dueDate);
			Order reloadedOrder = orderRepository.findById(seeded.orderId()).orElseThrow();
			assertThat(reloadedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(reloadedOrder.getExpiresAt()).isEqualTo(dueDate);
			assertThat(lastLogAction(payment.getId())).isEqualTo(PaymentReconcileAction.ISSUED);
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
					.willReturn(PaymentCancelResult.of("toss-key-compensate", "CANCELED", canceledAt));

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
		@DisplayName("READY 결제에 토스가 이미 취소됨이면 DONE 취소 행과 canceled_amount 를 함께 남긴다")
		void syncsCanceledWithDoneCancelRecord() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Payment payment = seedPayment(seeded.orderId(), PaymentStatus.READY, oldUpdatedAt());
			LocalDateTime approvedAt = now().minusMinutes(3).truncatedTo(ChronoUnit.SECONDS);
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.CANCELED, "toss-key-sync-canceled", "카드", seeded.finalAmount(), approvedAt,
					canceledAt, BigDecimal.ZERO, "txn-sync-canceled"));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment reloaded = paymentRepository.findById(payment.getId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(reloaded.getApprovedAt()).isEqualTo(approvedAt);
			assertThat(reloaded.getCanceledAmount()).isEqualByComparingTo(reloaded.getAmount());
			List<PaymentCancel> cancels = paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId());
			assertThat(cancels).hasSize(1);
			assertThat(cancels.get(0).getStatus()).isEqualTo(PaymentCancelStatus.DONE);
			assertThat(cancels.get(0).getCancelAmount()).isEqualByComparingTo(reloaded.getAmount());
			assertThat(cancels.get(0).getTossTransactionKey()).isEqualTo("txn-sync-canceled");
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

		@Test
		@DisplayName("CANCEL_REQUESTED 와 토스 CANCELED 면 주문·재고·결제를 함께 복구한다")
		void completesCancelRequestedWhenTossCanceled() {
			// given
			CancelSeededOrder seeded = seedCancelRequestedOrder(5);
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.CANCELED, seeded.paymentKey(), "카드", seeded.finalAmount(),
							now().minusMinutes(5), canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			assertThat(paymentRepository.findById(seeded.paymentId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
			assertThat(stockRepository.findByProductId(seeded.product().getId()).orElseThrow().getQuantity())
					.isEqualTo(5);
			assertThat(lastLogAction(seeded.paymentId())).isEqualTo(PaymentReconcileAction.CANCELED);
		}

		@Test
		@DisplayName("CANCEL_REQUESTED 와 토스 DONE 이면 취소를 한 번 재시도하고 CANCELED 로 수렴한다")
		void retriesCancelRequestedWhenTossDone() {
			// given
			CancelSeededOrder seeded = seedCancelRequestedOrder(5);
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, seeded.paymentKey(), "카드", seeded.finalAmount(),
							now().minusMinutes(5), null));
			given(paymentClient.cancel(firstRequestKeyOf(seeded)))
					.willReturn(PaymentCancelResult.of(seeded.paymentKey(), "CANCELED", canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			verify(paymentClient, times(1))
					.cancel(firstRequestKeyOf(seeded));
			assertThat(paymentRepository.findById(seeded.paymentId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.CANCELED);
		}

		@Test
		@DisplayName("레거시 키(cancel-{paymentKey}) 행만 REQUESTED 로 남은 결제도 그 키로 재시도한다")
		void retriesCancelRequestedWithLegacyKeyRow() {
			// given
			CancelSeededOrder seeded = seedCancelRequestedOrder(5);
			jdbcTemplate.update("update payment_cancel set idempotency_key = ? where payment_id = ?",
					"cancel-" + seeded.paymentKey(), seeded.paymentId());
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, seeded.paymentKey(), "카드", seeded.finalAmount(),
							now().minusMinutes(5), null));
			given(paymentClient.cancel(retryCommandMatcher(seeded.paymentKey(), "cancel-" + seeded.paymentKey())))
					.willReturn(PaymentCancelResult.of(seeded.paymentKey(), "CANCELED", canceledAt));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			verify(paymentClient, times(1))
					.cancel(retryCommandMatcher(seeded.paymentKey(), "cancel-" + seeded.paymentKey()));
			assertThat(paymentRepository.findById(seeded.paymentId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCELED);
		}

		@Test
		@DisplayName("취소 재시도가 거절되면 결제·주문을 DONE·PAID 로 되돌리고 MANUAL_REVIEW 한다")
		void revertsCancelRequestedWhenRetryRejected() {
			// given
			CancelSeededOrder seeded = seedCancelRequestedOrder(5);
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, seeded.paymentKey(), "카드", seeded.finalAmount(),
						now().minusMinutes(5), null));
			given(paymentClient.cancel(firstRequestKeyOf(seeded)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			assertThat(paymentRepository.findById(seeded.paymentId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.DONE);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PAID);
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getCancelReason()).isNull();
			PaymentReconcileLog log = lastLog(seeded.paymentId());
			assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
			assertThat(log.getDetail()).isEqualTo("토스가 취소를 거절");
		}

		@Test
		@DisplayName("취소 재시도 결과 불명이 상한에 도달하면 상태를 유지하고 FAILED 로 확정하지 않는다")
		void keepsCancelRequestedAtRetryLimitWhenRetryUnknown() {
			// given
			CancelSeededOrder seeded = seedCancelRequestedOrder(5);
			jdbcTemplate.update("update payment set reconcile_attempts = 9 where id = ?", seeded.paymentId());
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, seeded.paymentKey(), "카드", seeded.finalAmount(),
						now().minusMinutes(5), null));
			given(paymentClient.cancel(firstRequestKeyOf(seeded)))
					.willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			Payment payment = paymentRepository.findById(seeded.paymentId()).orElseThrow();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCEL_REQUESTED);
			assertThat(payment.getReconcileAttempts()).isEqualTo(10);
			assertThat(lastLogAction(seeded.paymentId())).isEqualTo(PaymentReconcileAction.MANUAL_REVIEW);
		}

		@Test
		@DisplayName("결과불명으로 REQUESTED 에 남은 부분취소는 같은 idempotencyKey 로 재시도돼 PARTIAL_CANCELED 로 반영된다")
		void retriesUnknownPartialCancelWithSameIdempotencyKeyAndCompletes() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Order order = orderRepository.findById(seeded.orderId()).orElseThrow();
			String paymentKey = "refund-recon-" + UUID.randomUUID();
			Payment payment = Payment.ready(order);
			payment.approve(paymentKey, "카드", now().minusMinutes(10));
			Payment savedPayment = paymentRepository.saveAndFlush(payment);
			// 결제 금액보다 1원 적게 취소해 PARTIAL_CANCELED 로 남긴다(전액이면 CANCELED 로 확정돼 버린다).
			BigDecimal cancelAmount = seeded.finalAmount().subtract(BigDecimal.ONE);
			PaymentRefundRequest refundRequest = paymentRefundWriter.requestRefund(savedPayment.getId(), cancelAmount,
					"부분 반품", null);
			jdbcTemplate.update("update payment_cancel set requested_at = ? where id = ?",
					Timestamp.valueOf(now().minusMinutes(2)), refundRequest.paymentCancelId());
			LocalDateTime canceledAt = now().minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
			// 다른 테스트가 남긴 payment_cancel 행이 같은 배치에 섞여 들어와도(공유 DB) 이 스텁은 내 idempotencyKey
			// 로만 반응하고, 다른 행은 매치 없이 넘어가 대사 자체는 실패하지 않는다.
			given(paymentClient.cancel(argThat(command -> refundRequest.idempotencyKey().equals(
					command.idempotencyKey())))).willReturn(new PaymentCancelResult(paymentKey, "PARTIAL_CANCELED",
							canceledAt, "txn-refund-retry-1", BigDecimal.ONE));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			verify(paymentClient).cancel(argThat((PaymentCancelCommand command) -> refundRequest.idempotencyKey()
					.equals(command.idempotencyKey())));
			Payment reloadedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
			assertThat(reloadedPayment.getCanceledAmount()).isEqualByComparingTo(cancelAmount);
			PaymentCancel paymentCancel = paymentCancelRepository.findById(refundRequest.paymentCancelId())
					.orElseThrow();
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
			assertThat(paymentCancel.getTossTransactionKey()).isEqualTo("txn-refund-retry-1");
		}

		@Test
		@DisplayName("재시도 상한을 넘기고 토스에 반영되지 않았으면 FAILED 로 닫아 새 부분취소가 막히지 않는다")
		void failsUnknownPartialCancelWhenNotAppliedSoNextRefundIsNotBlocked() {
			// given
			SeededOrder seeded = seedPendingOrder(5, 1);
			Order order = orderRepository.findById(seeded.orderId()).orElseThrow();
			String paymentKey = "refund-recon-notapplied-" + UUID.randomUUID();
			Payment payment = Payment.ready(order);
			payment.approve(paymentKey, "카드", now().minusMinutes(20));
			Payment savedPayment = paymentRepository.saveAndFlush(payment);
			BigDecimal cancelAmount = seeded.finalAmount().subtract(BigDecimal.ONE);
			PaymentRefundRequest refundRequest = paymentRefundWriter.requestRefund(savedPayment.getId(), cancelAmount,
					"부분 반품", null);
			// refundVerifyAfter(기본 10분)를 넘긴 11분 전 요청으로 만들어 재호출 대신 조회 확인 단계로 보낸다.
			jdbcTemplate.update("update payment_cancel set requested_at = ? where id = ?",
					Timestamp.valueOf(now().minusMinutes(11)), refundRequest.paymentCancelId());
			// 취소 전 잔액 그대로라 이번 취소는 토스에 반영되지 않았다. 재호출은 이미 멈췄으니 뒤늦게 적용될
			// 위험 없이 FAILED 로 닫아도 안전하다.
			given(paymentClient.lookup(seeded.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.DONE, paymentKey, "카드", null, null, null, seeded.finalAmount(), null));

			// when
			paymentReconcileScheduler.reconcile();

			// then
			PaymentCancel paymentCancel = paymentCancelRepository.findById(refundRequest.paymentCancelId())
					.orElseThrow();
			assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
			Payment reloadedPayment = paymentRepository.findById(savedPayment.getId()).orElseThrow();
			assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.DONE);
			assertThat(reloadedPayment.getCanceledAmount()).isEqualByComparingTo(BigDecimal.ZERO);

			// PAYMENT_CANCEL_IN_PROGRESS 가드가 풀려 같은 결제에 새 부분취소를 요청해도 더 이상 막히지 않는다.
			assertThatCode(() -> paymentRefundWriter.requestRefund(savedPayment.getId(), BigDecimal.ONE, "새 부분 반품",
					null)).doesNotThrowAnyException();
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
		@DisplayName("한정반 UNKNOWN 결제에 토스가 입금대기면 계좌를 닫아 FAILED 로 확정하고 만료 주기에 재고·한정 슬롯이 복구된다")
		void closesLimitedVirtualAccountThenExpiresAndRestoresLimitedSlot() {
			// given
			Product product = seedProduct(5);
			Long dropId = prepareOpenDrop(product, 5);
			Member member = memberRepository.save(MemberFixture.create("recon-" + UUID.randomUUID() + "@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(member));
			LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());
			Payment payment = seedPayment(purchase.orderId(), PaymentStatus.UNKNOWN, oldUpdatedAt());
			String paymentKey = "toss-key-limited-va-" + UUID.randomUUID();
			VirtualAccountInfo virtualAccount = new VirtualAccountInfo("088", "12345678901234", "홍길동",
					now().plusDays(3).truncatedTo(ChronoUnit.SECONDS), "secret-limited");
			given(paymentClient.lookup(purchase.orderNumber())).willReturn(new PaymentLookupResult(
					PaymentLookupStatus.WAITING_FOR_DEPOSIT, paymentKey, "가상계좌", purchase.finalAmount(), null, null,
					null, null, null, virtualAccount));
			given(paymentClient.cancel(eq(paymentKey), eq(LimitedVirtualAccountCloser.REASON)))
					.willReturn(PaymentCancelResult.of(paymentKey, "CANCELED", now()));

			try {
				// when
				paymentReconcileScheduler.reconcile();

				// then
				verify(paymentClient, times(1)).cancel(eq(paymentKey), eq(LimitedVirtualAccountCloser.REASON));
				Payment reloadedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
				assertThat(reloadedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
				assertThat(reloadedPayment.getFailReason()).isEqualTo(LimitedVirtualAccountCloser.REASON);
				assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(payment.getId())).isEmpty();
				PaymentReconcileLog log = lastLog(payment.getId());
				assertThat(log.getAction()).isEqualTo(PaymentReconcileAction.FAILED);
				assertThat(log.getTossStatus()).isEqualTo("CANCELED");
				assertThat(orderRepository.findById(purchase.orderId()).orElseThrow().getStatus())
						.isEqualTo(OrderStatus.PENDING);

				// when
				expireOrderNow(purchase.orderId());
				orderExpirationScheduler.expireOrders();

				// then
				assertThat(orderRepository.findById(purchase.orderId()).orElseThrow().getStatus())
						.isEqualTo(OrderStatus.CANCELED);
				assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(5);
				assertThat(limitedPurchaseRepository.findByOrderId(purchase.orderId())).isEmpty();
				assertThat(limitedDropRepository.findById(dropId).orElseThrow().getSoldCount()).isZero();
				assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("5");
				assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId),
						member.getId().toString())).isFalse();
			} finally {
				limitedDropRedisService.clear(dropId);
			}
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

	private PaymentCancelCommand firstRequestKeyOf(CancelSeededOrder seeded) {
		return retryCommandMatcher(seeded.paymentKey(), "cancel-" + seeded.paymentKey() + "-1");
	}

	private PaymentCancelCommand retryCommandMatcher(String paymentKey, String idempotencyKey) {
		return argThat(command -> command != null && paymentKey.equals(command.paymentKey())
				&& idempotencyKey.equals(command.idempotencyKey()));
	}

	private CancelSeededOrder seedCancelRequestedOrder(int stockQuantity) {
		SeededOrder seeded = seedPendingOrder(stockQuantity, 1);
		// markPaid() 는 상품주문(items)도 같이 옮기므로 findWithItemsById 로 지연 로딩 없이 가져온다
		Order order = orderRepository.findWithItemsById(seeded.orderId()).orElseThrow();
		order.markPaid();
		orderRepository.saveAndFlush(order);
		String paymentKey = "cancel-recon-" + UUID.randomUUID();
		Payment payment = Payment.ready(order);
		payment.approve(paymentKey, "카드", now().minusMinutes(5));
		Payment saved = paymentRepository.saveAndFlush(payment);
		paymentCancelWriter.requestCancel(seeded.orderId(), seeded.memberId(), "고객 변심");
		jdbcTemplate.update("update payment set updated_at = ? where id = ?",
				Timestamp.valueOf(oldUpdatedAt()), saved.getId());
		return new CancelSeededOrder(seeded.memberId(), seeded.product(), seeded.orderId(), seeded.orderNumber(),
				seeded.finalAmount(), saved.getId(), paymentKey);
	}

	private SeededOrder seedPendingOrder(int stockQuantity, int purchaseQuantity) {
		Member member = memberRepository.save(MemberFixture.create("recon-" + UUID.randomUUID() + "@groove.com"));
		Address address = addressRepository.save(AddressFixture.create(member));
		Product product = seedProduct(stockQuantity);
		OrderCreateResponse response = orderService.create(member.getId(),
				OrderFixture.directRequest(product.getId(), purchaseQuantity, address.getId()));
		return new SeededOrder(member.getId(), product, response.orderId(), response.orderNumber(),
				response.finalAmount());
	}

	private Product seedProduct(int stockQuantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, stockQuantity));
		return product;
	}

	private Long prepareOpenDrop(Product product, int totalQuantity) {
		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, totalQuantity);
		drop.open();
		LimitedDropFixture.withOpenAt(drop, now().minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now().plusHours(1));
		limitedDropRepository.saveAndFlush(drop);
		limitedDropRedisService.clear(drop.getId());
		limitedDropRedisService.initStock(drop.getId(), totalQuantity);
		return drop.getId();
	}

	private record SeededOrder(Long memberId, Product product, Long orderId, String orderNumber,
			BigDecimal finalAmount) {
	}

	private record CancelSeededOrder(Long memberId, Product product, Long orderId, String orderNumber,
			BigDecimal finalAmount, Long paymentId, String paymentKey) {
	}
}
