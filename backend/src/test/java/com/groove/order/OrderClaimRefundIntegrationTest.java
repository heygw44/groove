package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
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
import com.groove.order.dto.AdminOrderClaimCompleteRequest;
import com.groove.order.dto.AdminOrderClaimRejectRequest;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.dto.OrderItemResponse;
import com.groove.order.dto.OrderReturnRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderClaimStatus;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemAction;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.AdminOrderClaimService;
import com.groove.order.service.OrderItemClaimService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.repository.PaymentRepository;
import com.groove.payment.service.PaymentRefundWriter;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 클레임 환불이 토스에 나간 뒤 결과를 기다리는 동안(payment_cancel REQUESTED) 철회·거부·재승인이 환불과 어긋나지
 * 않는지 검증한다. 공유 DB 라 단언은 항상 자기 id 로만 한다.
 */
class OrderClaimRefundIntegrationTest extends IntegrationTestSupport {

	private static final BigDecimal PRICE = new BigDecimal("30000");
	private static final long LOCK_HOLD_MILLIS = 1000L;

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
	private OrderClaimRepository orderClaimRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseRepository limitedPurchaseRepository;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private OrderItemClaimService orderItemClaimService;

	@Autowired
	private AdminOrderClaimService adminOrderClaimService;

	@Autowired
	private PaymentRefundWriter paymentRefundWriter;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private Product saveProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist, "Claim Refund " + UUID.randomUUID(), PRICE);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, 10));
		return product;
	}

	private Member saveMember(String prefix) {
		return memberRepository.save(MemberFixture.create(prefix + UUID.randomUUID() + "@groove.com"));
	}

	/** 결제 완료 주문을 만든다. {@code preparingFlags} 의 true 인 상품은 발주확인(PREPARING) 상태로 둔다. */
	private SeededOrder seedPaidOrder(Member member, boolean... preparingFlags) {
		List<Product> products = new ArrayList<>();
		for (int i = 0; i < preparingFlags.length; i++) {
			products.add(saveProduct());
		}
		Order order = OrderFixture.createWithItems(member, products);
		order.markPaid();
		order.place(LocalDateTime.now(clock));
		for (int i = 0; i < preparingFlags.length; i++) {
			if (preparingFlags[i]) {
				order.getItems().get(i).confirmPreparing(LocalDateTime.now(clock));
			}
		}
		Order saved = orderRepository.saveAndFlush(order);
		paymentRepository.saveAndFlush(PaymentFixture.approved(saved, "toss-" + UUID.randomUUID()));
		List<Long> itemIds = saved.getItems().stream().map(OrderItem::getId).toList();
		return new SeededOrder(saved.getId(), itemIds, products);
	}

	private void stubCancelSuccess(long delayMillis) {
		given(paymentClient.cancel(any(PaymentCancelCommand.class))).willAnswer(invocation -> {
			if (delayMillis > 0) {
				Thread.sleep(delayMillis);
			}
			PaymentCancelCommand command = invocation.getArgument(0);
			return new PaymentCancelResult(command.paymentKey(), "PARTIAL_CANCELED", LocalDateTime.now(clock),
					"txn-" + command.idempotencyKey(), BigDecimal.ZERO);
		});
	}

	private void stubCancelUnknown() {
		given(paymentClient.cancel(any(PaymentCancelCommand.class)))
				.willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));
	}

	private List<String> refundStatuses(Long claimId) {
		return jdbcTemplate.queryForList("select status from payment_cancel where order_claim_id = ?",
				String.class, claimId);
	}

	private BigDecimal canceledAmountOf(Long paymentId) {
		return jdbcTemplate.queryForObject("select canceled_amount from payment where id = ?", BigDecimal.class,
				paymentId);
	}

	private OrderClaim reloadClaim(Long claimId) {
		return orderClaimRepository.findById(claimId).orElseThrow();
	}

	private OrderItemStatus reloadItemStatus(Long orderId, Long itemId) {
		return orderRepository.findWithItemsById(orderId).orElseThrow().getItems().stream()
				.filter(item -> item.getId().equals(itemId))
				.findFirst().orElseThrow().getStatus();
	}

	private OrderItemClaimStatus reloadItemClaimStatus(Long orderId, Long itemId) {
		return orderRepository.findWithItemsById(orderId).orElseThrow().getItems().stream()
				.filter(item -> item.getId().equals(itemId))
				.findFirst().orElseThrow().getClaimStatus();
	}

	private static Object errorCodeOf(Throwable throwable) {
		return ((BusinessException)throwable).getErrorCode();
	}

	@Nested
	@DisplayName("결과불명으로 남은 즉시 취소 환불")
	class UnknownImmediateRefund {

		@Test
		@DisplayName("구매자 철회·관리자 거부·관리자 재승인은 모두 ORDER_CLAIM_REFUND_IN_PROGRESS 로 막히고 클레임은 그대로 남는다")
		void blocksWithdrawRejectApproveWhileRefundPending() {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID()
					+ "@groove.com"));
			SeededOrder seeded = seedPaidOrder(buyer, false);
			Long itemId = seeded.itemIds().get(0);
			stubCancelUnknown();

			// when
			OrderItemResponse response = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemId,
					new OrderCancelRequest("고객 변심"));

			// then: 환불은 결과를 기다리는 상태로 남고 응답이 그 사실을 알린다
			Long claimId = response.claimId();
			assertThat(claimId).isNotNull();
			assertThat(response.refundInProgress()).isTrue();
			assertThat(response.availableActions()).doesNotContain(OrderItemAction.WITHDRAW_CLAIM);
			assertThat(refundStatuses(claimId)).containsExactly("REQUESTED");

			// when & then
			assertThatThrownBy(() -> orderItemClaimService.withdraw(buyer.getId(), claimId))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			assertThatThrownBy(() -> adminOrderClaimService.reject(admin.getId(), claimId,
					new AdminOrderClaimRejectRequest("거부")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			assertThatThrownBy(() -> adminOrderClaimService.approve(admin.getId(), claimId))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);

			assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);
			assertThat(reloadItemClaimStatus(seeded.orderId(), itemId)).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
			assertThat(refundStatuses(claimId)).containsExactly("REQUESTED");
			verify(paymentClient, times(1)).cancel(any(PaymentCancelCommand.class));
		}

		@Test
		@DisplayName("같은 주문의 다른 상품 취소 요청을 승인하면 409 로 막히고 그 클레임은 거부되지 않는다")
		void keepsOtherClaimRequestedWhenSiblingRefundPending() {
			// given: A 는 즉시 취소가 결과불명, B 는 발주확인 뒤 구매자 취소 요청
			Member buyer = saveMember("buyer-");
			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID()
					+ "@groove.com"));
			SeededOrder seeded = seedPaidOrder(buyer, false, true);
			Long itemA = seeded.itemIds().get(0);
			Long itemB = seeded.itemIds().get(1);
			OrderItemResponse requestB = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemB,
					new OrderCancelRequest("고객 변심"));
			stubCancelUnknown();
			OrderItemResponse cancelA = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemA,
					new OrderCancelRequest("고객 변심"));
			assertThat(cancelA.refundInProgress()).isTrue();

			// when & then
			Long claimB = requestB.claimId();
			assertThatThrownBy(() -> adminOrderClaimService.approve(admin.getId(), claimB))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isIn(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS, ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
			assertThat(reloadClaim(claimB).getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);
			assertThat(reloadItemClaimStatus(seeded.orderId(), itemB)).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
			assertThat(refundStatuses(claimB)).isEmpty();
			verify(paymentClient, times(1)).cancel(any(PaymentCancelCommand.class));
		}
	}

	@Nested
	@DisplayName("approve()")
	class Approve {

		@Test
		@DisplayName("같은 클레임을 동시에 두 번 승인해도 환불은 한 번만 나가고 한쪽은 예외로 끝난다")
		void refundsOnceWhenApprovedConcurrently() throws Exception {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID()
					+ "@groove.com"));
			SeededOrder seeded = seedPaidOrder(buyer, true);
			Long itemId = seeded.itemIds().get(0);
			Long claimId = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemId,
					new OrderCancelRequest("고객 변심")).claimId();
			stubCancelSuccess(200);

			// when
			int threads = 2;
			ExecutorService executorService = Executors.newFixedThreadPool(threads);
			CountDownLatch readyLatch = new CountDownLatch(threads);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<Throwable> failures = new ArrayList<>();
			List<Object> successes = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						Object result = adminOrderClaimService.approve(admin.getId(), claimId);
						synchronized (successes) {
							successes.add(result);
						}
					} catch (Throwable throwable) {
						synchronized (failures) {
							failures.add(throwable);
						}
					}
				});
			}
			readyLatch.await();
			startLatch.countDown();
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then
			assertThat(finished).isTrue();
			assertThat(successes).hasSize(1);
			assertThat(failures).hasSize(1);
			assertThat(failures.get(0)).isInstanceOf(BusinessException.class);
			assertThat(errorCodeOf(failures.get(0)))
					.isIn(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS, ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
			verify(paymentClient, times(1)).cancel(any(PaymentCancelCommand.class));
			assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.DONE);
			assertThat(refundStatuses(claimId)).containsExactly("DONE");
			assertThat(reloadItemStatus(seeded.orderId(), itemId)).isEqualTo(OrderItemStatus.CANCELED);
		}

		@Test
		@DisplayName("토스 취소는 성공했지만 클레임 마무리가 실패하면 환불과 클레임이 함께 REQUESTED 로 남고 재승인은 환불을 다시 내지 않는다")
		void keepsRefundAndClaimTogetherWhenFinalizeFails() {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID()
					+ "@groove.com"));
			SeededOrder seeded = seedPaidOrder(buyer, true);
			Long itemId = seeded.itemIds().get(0);
			Long claimId = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemId,
					new OrderCancelRequest("고객 변심")).claimId();
			stubCancelSuccess(0);
			// 재고 행이 없으면 클레임 마무리(재고 복원)가 실패해 completeRefund 가 통째로 롤백된다
			Long productId = seeded.products().get(0).getId();
			Stock stock = stockRepository.findByProductId(productId).orElseThrow();
			jdbcTemplate.update("delete from stock where id = ?", stock.getId());

			// when
			adminOrderClaimService.approve(admin.getId(), claimId);

			// then: 토스에는 나갔지만 DB 는 반영 전이라 환불·클레임이 같이 대기 상태로 남는다
			assertThat(refundStatuses(claimId)).containsExactly("REQUESTED");
			assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.REQUESTED);
			assertThat(reloadItemStatus(seeded.orderId(), itemId)).isEqualTo(OrderItemStatus.PREPARING);

			// when & then
			assertThatThrownBy(() -> adminOrderClaimService.approve(admin.getId(), claimId))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			verify(paymentClient, times(1)).cancel(any(PaymentCancelCommand.class));
		}
	}

	@Nested
	@DisplayName("complete()")
	class Complete {

		@Test
		@DisplayName("재입고 없이 반품을 완료하면 한정반 판매 수와 구매 이력을 되돌리지 않는다")
		void keepsLimitedPurchaseWhenReturnedWithoutRestock() {
			// given
			Member buyer = saveMember("buyer-");
			Member admin = memberRepository.save(MemberFixture.createAdmin("admin-" + UUID.randomUUID()
					+ "@groove.com"));
			Address address = addressRepository.save(AddressFixture.create(buyer));
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist, "Limited Return " + UUID.randomUUID(), PRICE);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			stockRepository.saveAndFlush(StockFixture.create(product, 5));
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 5, 2);
			drop.open();
			LocalDateTime now = LocalDateTime.now(clock);
			LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
			LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
			limitedDropRepository.saveAndFlush(drop);
			Long dropId = drop.getId();
			limitedDropRedisService.clear(dropId);
			limitedDropRedisService.initStock(dropId, 5);
			try {
				LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(dropId, buyer.getId(),
						address.getId());
				Order order = orderRepository.findWithItemsById(purchase.orderId()).orElseThrow();
				order.markPaid();
				order.place(now);
				OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
				OrderFixture.markFirstItemDeliveredAt(order, now.minusHours(1));
				Long itemId = order.getItems().get(0).getId();
				orderRepository.saveAndFlush(order);
				paymentRepository.save(PaymentFixture.approved(order, "toss-" + UUID.randomUUID()));
				stubCancelSuccess(0);
				int soldCountBefore = limitedDropRepository.findById(dropId).orElseThrow().getSoldCount();
				int stockBefore = stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity();

				// when
				Long claimId = orderItemClaimService.returnItem(buyer.getId(), purchase.orderId(), itemId,
						new OrderReturnRequest("단순 변심")).claimId();
				adminOrderClaimService.collect(admin.getId(), claimId);
				adminOrderClaimService.complete(admin.getId(), claimId, new AdminOrderClaimCompleteRequest(false));

				// then
				assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.DONE);
				assertThat(reloadItemStatus(purchase.orderId(), itemId)).isEqualTo(OrderItemStatus.RETURNED);
				assertThat(limitedDropRepository.findById(dropId).orElseThrow().getSoldCount())
						.isEqualTo(soldCountBefore);
				assertThat(limitedPurchaseRepository.existsByDropIdAndMemberId(dropId, buyer.getId())).isTrue();
				assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity())
						.isEqualTo(stockBefore);
			} finally {
				limitedDropRedisService.clear(dropId);
			}
		}
	}

	@Nested
	@DisplayName("completeRefund()")
	class CompleteRefund {

		@Test
		@DisplayName("같은 취소 건을 두 스레드가 동시에 확정해도 취소 누적액은 한 번만 늘어난다")
		void appliesCancelAmountOnceWhenCompletedConcurrently() throws Exception {
			// given
			Member buyer = saveMember("buyer-");
			SeededOrder seeded = seedPaidOrder(buyer, false, false, false);
			// 이중 반영이 잔액 초과 예외에 가려지지 않도록 상품 3개 중 1개만 취소한다
			Long itemId = seeded.itemIds().get(0);
			stubCancelUnknown();
			Long claimId = orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), itemId,
					new OrderCancelRequest("고객 변심")).claimId();
			assertThat(refundStatuses(claimId)).containsExactly("REQUESTED");
			Long paymentCancelId = jdbcTemplate.queryForObject(
					"select id from payment_cancel where order_claim_id = ?", Long.class, claimId);
			Long paymentId = jdbcTemplate.queryForObject(
					"select payment_id from payment_cancel where id = ?", Long.class, paymentCancelId);
			BigDecimal cancelAmount = jdbcTemplate.queryForObject(
					"select cancel_amount from payment_cancel where id = ?", BigDecimal.class, paymentCancelId);
			BigDecimal canceledBefore = canceledAmountOf(paymentId);
			LocalDateTime canceledAt = LocalDateTime.now(clock);

			// when
			int threads = 2;
			ExecutorService executorService = Executors.newFixedThreadPool(threads);
			CountDownLatch readyLatch = new CountDownLatch(threads);
			CountDownLatch startLatch = new CountDownLatch(1);
			for (int i = 0; i < threads; i++) {
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						paymentRefundWriter.completeRefund(paymentId, paymentCancelId, cancelAmount, "txn-race",
								canceledAt);
					} catch (Throwable ignored) {
						// 한쪽이 예외로 끝나는 것은 허용한다. 단언은 최종 상태로만 한다
					}
				});
			}
			// 주문 행을 잡아 두 스레드가 취소 건을 읽은 뒤 주문 락에서 함께 대기하게 만든다
			new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
				jdbcTemplate.queryForList("select id from orders where id = ? for update", seeded.orderId());
				try {
					readyLatch.await();
					startLatch.countDown();
					Thread.sleep(LOCK_HOLD_MILLIS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			});
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then
			assertThat(finished).isTrue();
			assertThat(canceledAmountOf(paymentId)).isEqualByComparingTo(canceledBefore.add(cancelAmount));
			assertThat(refundStatuses(claimId)).containsExactly("DONE");
			assertThat(reloadClaim(claimId).getStatus()).isEqualTo(OrderClaimStatus.DONE);
		}
	}

	private record SeededOrder(Long orderId, List<Long> itemIds, List<Product> products) {
	}
}
