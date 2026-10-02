package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.lifecycle.ShutdownSignal;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.dto.LimitedPurchaseResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.limited.service.LimitedDropRedisService;
import com.groove.limited.service.LimitedPurchaseService;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.order.service.OrderExpirationLock;
import com.groove.order.service.OrderExpirationService;
import com.groove.order.service.OrderService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class OrderExpirationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockHistoryRepository stockHistoryRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AddressRepository addressRepository;

	@Autowired
	private CouponRepository couponRepository;

	@Autowired
	private MemberCouponRepository memberCouponRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderExpirationScheduler orderExpirationScheduler;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private LimitedPurchaseService limitedPurchaseService;

	@Autowired
	private LimitedDropRedisService limitedDropRedisService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private Clock clock;

	@Autowired
	private OrderExpirationService orderExpirationService;

	@Autowired
	private OrderExpirationLock orderExpirationLock;

	@Autowired
	private ShutdownSignal shutdownSignal;

	private Long limitedDropId;

	@AfterEach
	void tearDown() {
		if (limitedDropId != null) {
			limitedDropRedisService.clear(limitedDropId);
		}
	}

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	private Product createProductWithStock(int quantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product createdProduct = ProductFixture.create(artist);
		albumRepository.save(createdProduct.getAlbum());
		Product product = productRepository.save(createdProduct);
		stockRepository.saveAndFlush(StockFixture.create(product, quantity));
		return product;
	}

	private Long prepareOpenDrop(Product product, int totalQuantity) {
		LimitedDrop drop = LimitedDropFixture.scheduled(product, totalQuantity, 1);
		drop.open();
		LocalDateTime now = LocalDateTime.now(clock);
		LimitedDropFixture.withOpenAt(drop, now.minusHours(1));
		LimitedDropFixture.withCloseAt(drop, now.plusHours(1));
		limitedDropRepository.saveAndFlush(drop);

		limitedDropId = drop.getId();
		limitedDropRedisService.clear(limitedDropId);
		limitedDropRedisService.initStock(limitedDropId, totalQuantity);
		return limitedDropId;
	}

	@Nested
	@DisplayName("expireOrders()")
	class ExpireOrders {

		@Test
		@DisplayName("쿠폰을 사용한 PENDING 주문이 만료되면 취소·재고 복구·쿠폰 복구가 모두 일어난다")
		void expiresOrderWithCouponAndRestoresStockAndCoupon() {
			// given
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = createProductWithStock(5);
			String couponCode = "EXPIRE" + UUID.randomUUID().toString().substring(0, 8);
			Coupon coupon = couponRepository.save(CouponFixture.fixed(couponCode, new BigDecimal("5000")));
			MemberCoupon memberCoupon = memberCouponRepository.save(MemberCouponFixture.create(member, coupon));

			OrderCreateResponse response = orderService.create(member.getId(),
					OrderFixture.directRequestWithCoupon(product.getId(), 1, address.getId(), memberCoupon.getId()));

			LocalDateTime now = LocalDateTime.now(clock);
			Order order = orderRepository.findById(response.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order canceled = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(canceled.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			assertThat(canceled.getCanceledAt()).isCloseTo(now, within(5, ChronoUnit.SECONDS));

			Stock reloadedStock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(5);
			assertThat(stockHistoryRepository.findAllByStockIdOrderByCreatedAtAsc(reloadedStock.getId())).anyMatch(
					history -> history.getChangeType() == StockChangeType.CANCEL
							&& history.getReason().equals("주문 취소 " + order.getOrderNumber()));

			MemberCoupon reloadedCoupon = memberCouponRepository.findById(memberCoupon.getId()).orElseThrow();
			assertThat(reloadedCoupon.isUsed()).isFalse();
		}

		@Test
		@DisplayName("PAID 주문은 만료 시각이 지났어도 건드리지 않는다")
		void doesNotTouchPaidOrderEvenIfExpiresAtPassed() {
			// given
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = createProductWithStock(5);

			OrderCreateResponse response = orderService.create(member.getId(),
					OrderFixture.directRequest(product.getId(), 1, address.getId()));

			LocalDateTime now = LocalDateTime.now(clock);
			Order order = orderRepository.findById(response.orderId()).orElseThrow();
			OrderFixture.markPaid(order);
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(reloaded.getCanceledAt()).isNull();

			Stock reloadedStock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(4);
		}

		@Test
		@DisplayName("PENDING 이지만 만료 시각이 아직 안 지났으면 건드리지 않는다")
		void doesNotTouchPendingOrderNotYetExpired() {
			// given
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = createProductWithStock(5);

			OrderCreateResponse response = orderService.create(member.getId(),
					OrderFixture.directRequest(product.getId(), 1, address.getId()));

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order reloaded = orderRepository.findById(response.orderId()).orElseThrow();
			assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);

			Stock reloadedStock = stockRepository.findByProductId(product.getId()).orElseThrow();
			assertThat(reloadedStock.getQuantity()).isEqualTo(4);
		}

		@Test
		@DisplayName("한정반 주문이 만료되면 커밋 뒤 Redis 선점도 함께 풀린다")
		void releasesLimitedDropRedisReservationWhenExpired() {
			// given
			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = createProductWithStock(5);
			Long dropId = prepareOpenDrop(product, 5);

			LimitedPurchaseResponse purchase = limitedPurchaseService.purchase(dropId, member.getId(),
					address.getId());
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("4");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId),
					member.getId().toString())).isTrue();

			LocalDateTime now = LocalDateTime.now(clock);
			Order order = orderRepository.findById(purchase.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(order, now.minusMinutes(1));
			orderRepository.saveAndFlush(order);

			// when
			orderExpirationScheduler.expireOrders();

			// then
			Order canceled = orderRepository.findById(order.getId()).orElseThrow();
			assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(redisTemplate.opsForValue().get(LimitedDropRedisService.stockKey(dropId))).isEqualTo("5");
			assertThat(redisTemplate.opsForSet().isMember(LimitedDropRedisService.buyersKey(dropId),
					member.getId().toString())).isFalse();
		}

		@Test
		@DisplayName("앞에 배치 크기 이상의 실패 주문이 있어도 한 번의 실행으로 뒤의 정상 주문을 만료하고 실패를 알린다")
		void expiresHealthyOrderBehindFailingBatch() {
			// given
			LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
			Member failingMember = createMember();
			List<Order> failingOrders = new ArrayList<>();
			for (int i = 0; i < OrderExpirationScheduler.BATCH_SIZE; i++) {
				Order failing = OrderFixture.create(failingMember,
						"20000101-EXP" + UUID.randomUUID().toString().substring(0, 8));
				failingOrders.add(OrderFixture.withExpiresAt(failing, base));
			}
			List<Long> failingIds = orderRepository.saveAllAndFlush(failingOrders).stream().map(Order::getId)
					.toList();

			Member member = createMember();
			Address address = addressRepository.save(AddressFixture.create(member));
			Product product = createProductWithStock(5);
			OrderCreateResponse response = orderService.create(member.getId(),
					OrderFixture.directRequest(product.getId(), 1, address.getId()));
			Order healthy = orderRepository.findById(response.orderId()).orElseThrow();
			OrderFixture.withExpiresAt(healthy, base.plusMinutes(1));
			orderRepository.saveAndFlush(healthy);

			// 컨텍스트를 새로 띄우면 공유 DB 가 재생성되므로 빈을 갈아끼우지 않고 스케줄러를 직접 만든다.
			OrderExpirationService failingService = mock(OrderExpirationService.class,
					delegatesTo(orderExpirationService));
			doThrow(new IllegalStateException("boom")).when(failingService)
					.expire(argThat(failingIds::contains), any());
			AlertNotifier alertNotifier = mock(AlertNotifier.class);
			OrderExpirationScheduler scheduler = new OrderExpirationScheduler(orderRepository, failingService,
					orderExpirationLock, shutdownSignal, clock, alertNotifier);

			// when
			scheduler.expireOrders();

			// then
			Order canceled = orderRepository.findById(healthy.getId()).orElseThrow();
			assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(canceled.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			assertThat(stockRepository.findByProductId(product.getId()).orElseThrow().getQuantity()).isEqualTo(5);
			ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
			verify(alertNotifier).notify(captor.capture());
			assertThat(captor.getValue().key()).isEqualTo("order.expiration-failed");
			assertThat(captor.getValue().targetId()).isEqualTo("orderId=" + failingIds.get(0));
		}
	}
}
