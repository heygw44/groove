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
import org.springframework.test.util.ReflectionTestUtils;

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
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderExpirationWriterTest {

	private static final Long ORDER_ID = 500L;
	private static final Long PAYMENT_ID = 30L;
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private OrderCancelRestorer orderCancelRestorer;

	@Mock
	private PaymentRepository paymentRepository;

	private OrderExpirationWriter writer;

	private Member member;
	private Product product;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		writer = new OrderExpirationWriter(orderRepository, paymentRepository, orderCancelRestorer);

		Clock clock = Clock.fixed(Instant.parse("2026-09-04T03:00:00Z"), ZONE);
		now = LocalDateTime.now(clock);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("checkExpirable()")
	class CheckExpirable {

		@Test
		@DisplayName("만료된 PENDING 주문이고 결제가 없으면 그 자리에서 취소·복원하고 단순 대상을 반환한다")
		void expiresAndRestoresWhenPending() {
			// given
			Order order = expiredOrder();
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			assertThat(target).isPresent();
			assertThat(target.get().waitingForDeposit()).isFalse();
			verify(orderCancelRestorer).restore(order, false);
		}

		@Test
		@DisplayName("주문을 찾을 수 없으면 빈 값을 반환한다")
		void returnsEmptyWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(target).isEmpty();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("이미 PAID 인 주문이면 빈 값을 반환한다")
		void returnsEmptyWhenAlreadyPaid() {
			// given
			Order order = OrderFixture.withId(OrderFixture.markPaid(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1),
							now.minusMinutes(1))), ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(target).isEmpty();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("PENDING 이지만 아직 만료 시각 전이면 빈 값을 반환한다")
		void returnsEmptyWhenNotYetExpired() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.plusMinutes(1)),
					ORDER_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(target).isEmpty();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("결제가 READY/UNKNOWN 이면 대사가 결론을 낼 때까지 만료를 건너뛰고 빈 값을 반환한다")
		void skipsWhenPaymentIsUnresolved() {
			// given
			Order order = expiredOrder();
			Payment payment = PaymentFixture.unknown(order, "확인 중");
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(target).isEmpty();
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		@Test
		@DisplayName("결제가 WAITING_FOR_DEPOSIT 이면 상태를 바꾸지 않고 가상계좌 대상을 반환한다")
		void returnsVirtualAccountTargetWithoutChangingStateWhenWaitingForDeposit() {
			// given
			Order order = expiredOrder();
			Payment payment = paymentWithId(waitingForDepositPayment(order), PAYMENT_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(payment));

			// when
			Optional<OrderExpirationTarget> target = writer.checkExpirable(ORDER_ID, now);

			// then
			assertThat(target).isPresent();
			assertThat(target.get().waitingForDeposit()).isTrue();
			assertThat(target.get().paymentId()).isEqualTo(PAYMENT_ID);
			assertThat(target.get().tossOrderId()).isEqualTo(order.getOrderNumber());
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_FOR_DEPOSIT);
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}

		private Order expiredOrder() {
			return OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.minusMinutes(1)),
					ORDER_ID);
		}
	}

	@Nested
	@DisplayName("finalizeVirtualAccountExpiry()")
	class FinalizeVirtualAccountExpiry {

		@Test
		@DisplayName("여전히 WAITING_FOR_DEPOSIT 이면 계좌를 닫힌 것으로 기록하고 주문을 취소·복원한다")
		void cancelsPaymentAndOrderWhenStillWaiting() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.minusMinutes(1)),
					ORDER_ID);
			Payment payment = paymentWithId(waitingForDepositPayment(order), PAYMENT_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			boolean result = writer.finalizeVirtualAccountExpiry(ORDER_ID, PAYMENT_ID, "입금기한 만료", now);

			// then
			assertThat(result).isTrue();
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
			verify(orderCancelRestorer).restore(order, false);
		}

		@Test
		@DisplayName("그 사이 입금이 확인돼 더 이상 WAITING_FOR_DEPOSIT 이 아니면 아무것도 하지 않고 false 를 반환한다")
		void doesNothingWhenAlreadyResolved() {
			// given
			Order order = OrderFixture.withId(
					OrderFixture.withExpiresAt(OrderFixture.createWithItem(member, product, 1), now.minusMinutes(1)),
					ORDER_ID);
			Payment payment = paymentWithId(PaymentFixture.approved(order), PAYMENT_ID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

			// when
			boolean result = writer.finalizeVirtualAccountExpiry(ORDER_ID, PAYMENT_ID, "입금기한 만료", now);

			// then
			assertThat(result).isFalse();
			verify(orderCancelRestorer, never()).restore(any(), anyBoolean());
		}
	}

	private Payment waitingForDepositPayment(Order order) {
		Payment payment = Payment.ready(order);
		payment.issueVirtualAccount(PaymentFixture.PAYMENT_KEY, "가상계좌", "088", "12345678901234", "홍길동",
				now.plusHours(24), "hash");
		return payment;
	}

	private Payment paymentWithId(Payment payment, Long id) {
		ReflectionTestUtils.setField(payment, "id", id);
		return payment;
	}
}
