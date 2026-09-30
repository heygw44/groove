package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.AdminOrderItemBulkResultResponse;
import com.groove.order.dto.AdminOrderItemConfirmRequest;
import com.groove.order.dto.AdminOrderItemShipRequest;
import com.groove.order.dto.OrderCancelRequest;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderClaim;
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderClaimRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.AdminOrderItemService;
import com.groove.order.service.OrderCancelService;
import com.groove.order.service.OrderItemClaimService;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelCommand;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 관리자 발주확인·발송이 구매자 취소와 겹칠 때 주문 락 뒤의 상태를 기준으로 판단하는지 검증한다. 공유 DB 라 단언은
 * 항상 자기 id 로만 한다.
 */
class OrderItemLockOrderingIntegrationTest extends IntegrationTestSupport {

	private static final BigDecimal PRICE = new BigDecimal("30000");
	private static final Long ADMIN_ID = 1L;
	private static final long HOLD_MILLIS = 700L;
	private static final long TIMEOUT_SECONDS = 20L;

	@Autowired
	private MemberRepository memberRepository;

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
	private AdminOrderItemService adminOrderItemService;

	@Autowired
	private OrderCancelService orderCancelService;

	@Autowired
	private OrderItemClaimService orderItemClaimService;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	@MockitoBean
	private PaymentClient paymentClient;

	private Member saveBuyer() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	private SeededOrder seedPaidOrder(Member member) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product created = ProductFixture.create(artist, "Lock Order " + UUID.randomUUID(), PRICE);
		albumRepository.save(created.getAlbum());
		Product product = productRepository.save(created);
		stockRepository.saveAndFlush(StockFixture.create(product, 10));
		Order order = OrderFixture.createWithItems(member, List.of(product));
		order.markPaid();
		order.place(LocalDateTime.now(clock));
		Order saved = orderRepository.saveAndFlush(order);
		paymentRepository.saveAndFlush(PaymentFixture.approved(saved, "toss-" + UUID.randomUUID()));
		return new SeededOrder(saved.getId(), saved.getItems().get(0).getId());
	}

	private OrderItem reloadItem(SeededOrder seeded) {
		return orderRepository.findWithItemsById(seeded.orderId()).orElseThrow().getItems().stream()
				.filter(item -> item.getId().equals(seeded.itemId()))
				.findFirst().orElseThrow();
	}

	private AdminOrderItemBulkResultResponse confirm(SeededOrder seeded) {
		return adminOrderItemService.confirmPreparing(ADMIN_ID,
				new AdminOrderItemConfirmRequest(List.of(seeded.itemId())));
	}

	private AdminOrderItemBulkResultResponse ship(SeededOrder seeded) {
		return adminOrderItemService.startShipping(ADMIN_ID, new AdminOrderItemShipRequest(
				List.of(new AdminOrderItemShipRequest.ShipItem(seeded.itemId(), "CJ", "123456789012"))));
	}

	private void stubCancelUnknown() {
		given(paymentClient.cancel(any(PaymentCancelCommand.class)))
				.willThrow(new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN));
	}

	@Nested
	@DisplayName("startShipping()")
	class StartShipping {

		@Test
		@DisplayName("구매자 취소 요청이 락을 쥔 채 커밋되기를 기다린 발송은 커밋된 클레임 표시를 보고 건너뛴다")
		void skipsItemClaimedWhileWaitingForOrderLock() throws Exception {
			// given
			SeededOrder seeded = seedPaidOrder(saveBuyer());
			CountDownLatch locked = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);
			TransactionTemplate template = new TransactionTemplate(transactionManager);
			try {
				Future<?> canceler = executor.submit(() -> template.executeWithoutResult(status -> {
					orderRepository.findByIdForUpdate(seeded.orderId()).orElseThrow();
					Order order = orderRepository.findWithItemsById(seeded.orderId()).orElseThrow();
					OrderItem item = order.getItems().get(0);
					orderClaimRepository.save(OrderClaim.requestCancel(item, "고객 변심", null,
							LocalDateTime.now(clock)));
					item.markClaimRequested(OrderItemClaimStatus.CANCEL_REQUEST);
					locked.countDown();
					sleepQuietly(HOLD_MILLIS);
				}));
				Future<AdminOrderItemBulkResultResponse> shipper = executor.submit(() -> {
					assertThat(locked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
					return ship(seeded);
				});

				// when
				AdminOrderItemBulkResultResponse result = shipper.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
				canceler.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

				// then
				assertThat(result.processed()).isZero();
				assertThat(result.skipped()).isEqualTo(1);
				OrderItem item = reloadItem(seeded);
				assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
				assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
				assertThat(item.getTrackingNumber()).isNull();
			} finally {
				executor.shutdownNow();
			}
		}

		@Test
		@DisplayName("전액취소가 결과불명으로 남은 주문의 상품은 발송하지 않고 건너뛴다")
		void skipsItemOfCancelRequestedOrder() {
			// given
			Member buyer = saveBuyer();
			SeededOrder seeded = seedPaidOrder(buyer);
			stubCancelUnknown();
			orderCancelService.cancel(buyer.getId(), seeded.orderId(), new OrderCancelRequest("고객 변심"));
			assertThat(paymentRepository.findByOrderId(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(PaymentStatus.CANCEL_REQUESTED);

			// when
			AdminOrderItemBulkResultResponse result = ship(seeded);

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			OrderItem item = reloadItem(seeded);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
			assertThat(item.getTrackingNumber()).isNull();
			assertThat(orderRepository.findById(seeded.orderId()).orElseThrow().getStatus())
					.isEqualTo(OrderStatus.PAID);
		}
	}

	@Nested
	@DisplayName("confirmPreparing()")
	class ConfirmPreparing {

		@Test
		@DisplayName("전액취소가 결과불명으로 남은 주문의 상품은 발주확인하지 않고 건너뛴다")
		void skipsItemOfCancelRequestedOrder() {
			// given
			Member buyer = saveBuyer();
			SeededOrder seeded = seedPaidOrder(buyer);
			stubCancelUnknown();
			orderCancelService.cancel(buyer.getId(), seeded.orderId(), new OrderCancelRequest("고객 변심"));

			// when
			AdminOrderItemBulkResultResponse result = confirm(seeded);

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			assertThat(reloadItem(seeded).getStatus()).isEqualTo(OrderItemStatus.PAID);
		}

		@Test
		@DisplayName("결과불명 환불을 기다리는 즉시 취소 클레임이 있는 상품은 발주확인하지 않고 건너뛴다")
		void skipsItemWithPendingImmediateCancel() {
			// given
			Member buyer = saveBuyer();
			SeededOrder seeded = seedPaidOrder(buyer);
			stubCancelUnknown();
			orderItemClaimService.cancel(buyer.getId(), seeded.orderId(), seeded.itemId(),
					new OrderCancelRequest("고객 변심"));

			// when
			AdminOrderItemBulkResultResponse result = confirm(seeded);

			// then
			assertThat(result.processed()).isZero();
			assertThat(result.skipped()).isEqualTo(1);
			OrderItem item = reloadItem(seeded);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
			assertThat(item.getClaimStatus()).isEqualTo(OrderItemClaimStatus.CANCEL_REQUEST);
		}
	}

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private record SeededOrder(Long orderId, Long itemId) {
	}
}
