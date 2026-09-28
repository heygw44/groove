package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.PaymentFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderCancelRestorer orderCancelRestorer;

	@Mock
	private PaymentRepository paymentRepository;

	private OrderExpirationService orderExpirationService;

	private Member member;
	private Product product;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		orderExpirationService = new OrderExpirationService(orderRepository, orderCancelRestorer, paymentRepository);

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
		@DisplayName("만료된 PENDING 주문이면 취소하고 재고·쿠폰·한정반 선점을 복구한 뒤 true 를 반환한다")
		void expiresAndRestoresWhenPending() {
			// given
			Order order = expiredOrder();
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			boolean result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			assertThat(result).isTrue();
			verify(orderCancelRestorer).restore(order, false);
		}

		@Test
		@DisplayName("주문을 찾을 수 없으면 아무것도 하지 않고 false 를 반환한다")
		void returnsFalseWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when
			boolean result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isFalse();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("이미 PAID 인 주문이면 아무것도 하지 않고 false 를 반환한다")
		void returnsFalseWhenAlreadyPaid() {
			// given
			Order order = OrderFixture.withId(OrderFixture.markPaid(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1),
							now.minusMinutes(1))), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			boolean result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isFalse();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("PENDING 이지만 아직 만료 시각 전이면 아무것도 하지 않고 false 를 반환한다")
		void returnsFalseWhenNotYetExpired() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1),
							now.plusMinutes(1)), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			boolean result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isFalse();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("결제가 READY/UNKNOWN 이면 대사가 결론을 낼 때까지 만료를 건너뛰고 false 를 반환한다")
		void skipsWhenPaymentIsUnresolved() {
			// given
			Order order = expiredOrder();
			Payment payment = PaymentFixture.unknown(order, "확인 중");
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			boolean result = orderExpirationService.expire(ORDER_ID, now);

			// then
			assertThat(result).isFalse();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}
	}

	private Order expiredOrder() {
		return OrderFixture.withId(
				OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.minusMinutes(1)),
				ORDER_ID);
	}
}
