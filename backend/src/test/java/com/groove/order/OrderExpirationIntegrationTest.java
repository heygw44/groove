package com.groove.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.groove.coupon.entity.Coupon;
import com.groove.coupon.entity.MemberCoupon;
import com.groove.coupon.repository.CouponRepository;
import com.groove.coupon.repository.MemberCouponRepository;
import com.groove.fixture.AddressFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.entity.StockChangeType;
import com.groove.inventory.repository.StockHistoryRepository;
import com.groove.inventory.repository.StockRepository;
import com.groove.member.entity.Address;
import com.groove.member.entity.Member;
import com.groove.member.repository.AddressRepository;
import com.groove.member.repository.MemberRepository;
import com.groove.order.dto.OrderCreateResponse;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.scheduler.OrderExpirationScheduler;
import com.groove.order.service.OrderService;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

class OrderExpirationIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

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
	private Clock clock;

	private Member createMember() {
		return memberRepository.save(MemberFixture.create("buyer-" + UUID.randomUUID() + "@groove.com"));
	}

	private Product createProductWithStock(int quantity) {
		Artist artist = artistRepository.save(ArtistFixture.create());
		Product product = productRepository.save(ProductFixture.create(artist));
		stockRepository.saveAndFlush(StockFixture.create(product, quantity));
		return product;
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
	}
}
