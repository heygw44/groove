package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderStockService orderStockService;

	@Mock
	private LimitedPurchaseWriter limitedPurchaseWriter;

	private OrderExpirationService orderExpirationService;

	private Member member;
	private Product product;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		orderExpirationService = new OrderExpirationService(orderRepository, orderStockService,
				limitedPurchaseWriter);

		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("expire()")
	class Expire {

		@Test
		@DisplayName("만료된 PENDING 주문이면 취소하고 재고를 복구한다")
		void expiresAndRestoresStockWhenPending() {
			// given
			Order order = expiredOrder();
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(limitedPurchaseWriter.revertByOrder(ORDER_ID, now)).willReturn(Optional.empty());

			// when
			Optional<LimitedRelease> result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			assertThat(result).isEmpty();
			verify(orderStockService).restore(order);
		}

		@Test
		@DisplayName("쿠폰을 사용한 주문이 만료되면 쿠폰이 미사용 상태로 복구된다")
		void restoresCouponWhenExpired() {
			// given
			Order order = expiredOrder();
			MemberCoupon memberCoupon = MemberCouponFixture.create(member,
					CouponFixture.fixed("EXPIRE5000", new BigDecimal("5000")));
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));
			memberCoupon.use(order.getId());
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(limitedPurchaseWriter.revertByOrder(ORDER_ID, now)).willReturn(Optional.empty());

			// when
			orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(memberCoupon.isUsed()).isFalse();
		}

		@Test
		@DisplayName("한정반 주문이면 revertByOrder 결과를 그대로 반환한다")
		void returnsLimitedReleaseFromWriter() {
			// given
			Order order = expiredOrder();
			LimitedRelease release = new LimitedRelease(10L, member.getId());
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(limitedPurchaseWriter.revertByOrder(ORDER_ID, now)).willReturn(Optional.of(release));

			// when
			Optional<LimitedRelease> result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).contains(release);
		}

		@Test
		@DisplayName("주문을 찾을 수 없으면 아무것도 하지 않고 empty 를 반환한다")
		void returnsEmptyWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when
			Optional<LimitedRelease> result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isEmpty();
			verify(orderStockService, never()).restore(any());
			verify(limitedPurchaseWriter, never()).revertByOrder(any(), any());
		}

		@Test
		@DisplayName("이미 PAID 인 주문이면 아무것도 하지 않고 empty 를 반환한다")
		void returnsEmptyWhenAlreadyPaid() {
			// given
			Order order = OrderFixture.withId(OrderFixture.markPaid(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1),
							now.minusMinutes(1))), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Optional<LimitedRelease> result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isEmpty();
			verify(orderStockService, never()).restore(any());
		}

		@Test
		@DisplayName("PENDING 이지만 아직 만료 시각 전이면 아무것도 하지 않고 empty 를 반환한다")
		void returnsEmptyWhenNotYetExpired() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1),
							now.plusMinutes(1)), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Optional<LimitedRelease> result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isEmpty();
			verify(orderStockService, never()).restore(any());
		}
	}

	private Order expiredOrder() {
		return OrderFixture.withId(
				OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.minusMinutes(1)),
				ORDER_ID);
	}
}
