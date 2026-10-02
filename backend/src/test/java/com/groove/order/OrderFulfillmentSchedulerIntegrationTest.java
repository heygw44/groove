package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.config.OrderAutoDeliverProperties;
import com.groove.order.config.OrderPurchaseConfirmProperties;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderAutoDeliverScheduler;
import com.groove.order.scheduler.OrderPurchaseConfirmScheduler;
import com.groove.order.service.OrderAutoDeliverLock;
import com.groove.order.service.OrderAutoDeliverService;
import com.groove.order.service.OrderPurchaseConfirmLock;
import com.groove.order.service.OrderPurchaseConfirmService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 자동 배송완료·자동 구매확정 스케줄러가 named lock 을 실제로 획득해 동작하는지 확인한다(D7). 잠금 자체의
 * 성공·실패 경로는 {@code NamedLockTest}/{@code NamedLockIntegrationTest} 가 이미 검증하므로, 여기서는
 * 스케줄러가 후보를 찾아 실제 전이까지 끝내는 흐름만 본다.
 */
class OrderFulfillmentSchedulerIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderAutoDeliverScheduler orderAutoDeliverScheduler;

	@Autowired
	private OrderPurchaseConfirmScheduler orderPurchaseConfirmScheduler;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private OrderPurchaseConfirmService orderPurchaseConfirmService;

	@Autowired
	private OrderPurchaseConfirmLock orderPurchaseConfirmLock;

	@Autowired
	private OrderAutoDeliverService orderAutoDeliverService;

	@Autowired
	private OrderAutoDeliverLock orderAutoDeliverLock;

	@Autowired
	private ShutdownSignal shutdownSignal;

	@Autowired
	private Clock clock;

	private Product createProduct() {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		return productRepository.save(createdProduct);
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("fulfillment-" + UUID.randomUUID() + "@groove.com"));
	}

	/** 공유 DB 라 {@link OrderFixture#createWithItem} 의 고정 주문번호를 그대로 쓰면 unique 제약에 걸린다. */
	private Order createOrderWithItem(Member member, Product product) {
		Order order = OrderFixture.create(member, "20330601-FUL" + UUID.randomUUID().toString().substring(0, 8));
		order.addItem(product, 1);
		return order;
	}

	@Nested
	@DisplayName("deliverOrders()")
	class DeliverOrders {

		@Test
		@DisplayName("발송 뒤 설정 일수가 지난 상품주문을 배송완료로 자동 처리한다")
		void deliversEligibleItem() {
			// given
			Product product = createProduct();
			Order order = createOrderWithItem(createMember(), product);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			OrderFixture.markFirstItemShippedAt(order, LocalDateTime.now(clock).minusDays(6));
			orderRepository.saveAndFlush(order);
			Long orderId = order.getId();

			// when
			orderAutoDeliverScheduler.deliverOrders();

			// then
			Order reloaded = orderRepository.findWithItemsById(orderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
		}

		@Test
		@DisplayName("발송 뒤 설정 일수가 지나지 않은 상품주문은 건드리지 않는다")
		void doesNotDeliverIneligibleItem() {
			// given
			Product product = createProduct();
			Order order = createOrderWithItem(createMember(), product);
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			OrderFixture.markFirstItemShippedAt(order, LocalDateTime.now(clock).minusHours(1));
			orderRepository.saveAndFlush(order);
			Long orderId = order.getId();

			// when
			orderAutoDeliverScheduler.deliverOrders();

			// then
			Order reloaded = orderRepository.findWithItemsById(orderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
		}

		@Test
		@DisplayName("앞에 배치 크기 이상의 실패 행이 있어도 뒤의 정상 건을 배송완료하고 실패를 알린다")
		void deliversHealthyItemsBehindFailingBatch() {
			// given
			LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
			Long failingFirstId = saveShippingItem(base);
			Long failingSecondId = saveShippingItem(base);
			Long healthyItemId = saveShippingItem(base.plusMinutes(1));
			Long healthyOrderId = orderItemRepository.findOrderIdById(healthyItemId).orElseThrow();
			// 컨텍스트를 새로 띄우면 공유 DB 가 재생성되므로 빈을 갈아끼우지 않고 배치 크기 2 스케줄러를 직접 만든다.
			OrderAutoDeliverService failingService = mock(OrderAutoDeliverService.class,
					delegatesTo(orderAutoDeliverService));
			doThrow(new IllegalStateException("boom")).when(failingService)
					.deliver(eq(failingFirstId), any(), any());
			doThrow(new IllegalStateException("boom")).when(failingService)
					.deliver(eq(failingSecondId), any(), any());
			AlertNotifier alertNotifier = mock(AlertNotifier.class);
			OrderAutoDeliverScheduler scheduler = new OrderAutoDeliverScheduler(orderItemRepository, failingService,
					orderAutoDeliverLock, new OrderAutoDeliverProperties(5, Duration.ofMinutes(5), 2), shutdownSignal,
					clock, alertNotifier);

			// when
			scheduler.deliverOrders();

			// then
			Order reloaded = orderRepository.findWithItemsById(healthyOrderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(captor.capture());
			assertThat(captor.getValue().key()).isEqualTo("order.auto-deliver-failed");
			assertThat(captor.getValue().summary()).contains(failingFirstId.toString(), failingSecondId.toString());
		}

		private Long saveShippingItem(LocalDateTime shippedAt) {
			Order order = createOrderWithItem(createMember(), createProduct());
			OrderFixture.markItemsStatus(order, OrderItemStatus.SHIPPING);
			OrderFixture.markFirstItemShippedAt(order, shippedAt);
			orderRepository.saveAndFlush(order);
			return order.getItems().get(0).getId();
		}
	}

	@Nested
	@DisplayName("confirmPurchases()")
	class ConfirmPurchases {

		@Test
		@DisplayName("배송완료 뒤 설정 일수가 지난 상품주문을 구매확정으로 자동 처리한다")
		void confirmsEligibleItem() {
			// given
			Product product = createProduct();
			Order order = createOrderWithItem(createMember(), product);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			OrderFixture.markFirstItemDeliveredAt(order, LocalDateTime.now(clock).minusDays(9));
			orderRepository.saveAndFlush(order);
			Long orderId = order.getId();

			// when
			orderPurchaseConfirmScheduler.confirmPurchases();

			// then
			Order reloaded = orderRepository.findWithItemsById(orderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PURCHASE_CONFIRMED);
		}

		@Test
		@DisplayName("배송완료 뒤 설정 일수가 지나지 않은 상품주문은 건드리지 않는다")
		void doesNotConfirmIneligibleItem() {
			// given
			Product product = createProduct();
			Order order = createOrderWithItem(createMember(), product);
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			OrderFixture.markFirstItemDeliveredAt(order, LocalDateTime.now(clock).minusHours(1));
			orderRepository.saveAndFlush(order);
			Long orderId = order.getId();

			// when
			orderPurchaseConfirmScheduler.confirmPurchases();

			// then
			Order reloaded = orderRepository.findWithItemsById(orderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
		}

		@Test
		@DisplayName("앞에 배치 크기 이상의 실패 행이 있어도 뒤의 정상 건을 구매확정하고 실패를 알린다")
		void confirmsHealthyItemsBehindFailingBatch() {
			// given
			LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
			Long failingFirstId = saveDeliveredItem(base);
			Long failingSecondId = saveDeliveredItem(base);
			Long healthyItemId = saveDeliveredItem(base.plusMinutes(1));
			Long healthyOrderId = orderItemRepository.findOrderIdById(healthyItemId).orElseThrow();
			// 컨텍스트를 새로 띄우면 공유 DB 가 재생성되므로 빈을 갈아끼우지 않고 배치 크기 2 스케줄러를 직접 만든다.
			OrderPurchaseConfirmService failingService = mock(OrderPurchaseConfirmService.class,
					delegatesTo(orderPurchaseConfirmService));
			doThrow(new IllegalStateException("boom")).when(failingService)
					.confirm(eq(failingFirstId), any(), any());
			doThrow(new IllegalStateException("boom")).when(failingService)
					.confirm(eq(failingSecondId), any(), any());
			AlertNotifier alertNotifier = mock(AlertNotifier.class);
			OrderPurchaseConfirmScheduler scheduler = new OrderPurchaseConfirmScheduler(orderItemRepository,
					failingService, orderPurchaseConfirmLock, new OrderPurchaseConfirmProperties(8, "-", 2),
					shutdownSignal, clock, alertNotifier);

			// when
			scheduler.confirmPurchases();

			// then
			Order reloaded = orderRepository.findWithItemsById(healthyOrderId).orElseThrow();
			assertThat(reloaded.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PURCHASE_CONFIRMED);
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(captor.capture());
			assertThat(captor.getValue().key()).isEqualTo("order.purchase-confirm-failed");
			assertThat(captor.getValue().summary()).contains(failingFirstId.toString(), failingSecondId.toString());
		}

		private Long saveDeliveredItem(LocalDateTime deliveredAt) {
			Order order = createOrderWithItem(createMember(), createProduct());
			OrderFixture.markItemsStatus(order, OrderItemStatus.DELIVERED);
			OrderFixture.markFirstItemDeliveredAt(order, deliveredAt);
			orderRepository.saveAndFlush(order);
			return order.getItems().get(0).getId();
		}
	}
}
