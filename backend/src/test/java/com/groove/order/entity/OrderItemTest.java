package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class OrderItemTest {

	private final Member member = MemberFixture.create();
	private final Artist artist = ArtistFixture.create();

	private OrderItem createItem() {
		Product product = ProductFixture.create(artist);
		Order order = OrderFixture.createWithItem(member, product, 1);
		return order.getItems().get(0);
	}

	private void setStatus(OrderItem item, OrderItemStatus status) {
		ReflectionTestUtils.setField(item, "status", status);
	}

	@Nested
	@DisplayName("of()")
	class Of {

		@Test
		@DisplayName("생성하면 PAYMENT_PENDING 상태이고 discountShare 는 0 이다")
		void createsWithPaymentPendingStatusAndZeroDiscountShare() {
			// given & when
			OrderItem item = createItem();

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAYMENT_PENDING);
			assertThat(item.getDiscountShare()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(item.getProductOrderNumber()).endsWith("-01");
		}
	}

	@Nested
	@DisplayName("markPaid()")
	class MarkPaid {

		@Test
		@DisplayName("PAYMENT_PENDING 이면 PAID 로 바뀐다")
		void changesToPaidFromPaymentPending() {
			// given
			OrderItem item = createItem();

			// when
			item.markPaid();

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
		}

		@Test
		@DisplayName("PAYMENT_WAITING 이면 PAID 로 바뀐다")
		void changesToPaidFromPaymentWaiting() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PAYMENT_WAITING);

			// when
			item.markPaid();

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAID);
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemStatus.class,
				names = {"CANCELED", "CANCELED_BY_NOPAYMENT", "PURCHASE_CONFIRMED", "RETURNED"})
		@DisplayName("이미 CANCELED 류이면 건드리지 않는다")
		void doesNothingWhenAlreadyTerminal(OrderItemStatus terminal) {
			// given
			OrderItem item = createItem();
			setStatus(item, terminal);

			// when
			item.markPaid();

			// then
			assertThat(item.getStatus()).isEqualTo(terminal);
		}
	}

	@Nested
	@DisplayName("awaitDeposit()")
	class AwaitDeposit {

		@Test
		@DisplayName("PAYMENT_PENDING 이면 PAYMENT_WAITING 으로 바뀐다")
		void changesToPaymentWaiting() {
			// given
			OrderItem item = createItem();

			// when
			item.awaitDeposit();

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PAYMENT_WAITING);
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemStatus.class,
				names = {"CANCELED", "CANCELED_BY_NOPAYMENT", "PURCHASE_CONFIRMED", "RETURNED"})
		@DisplayName("이미 CANCELED 류이면 건드리지 않는다")
		void doesNothingWhenAlreadyTerminal(OrderItemStatus terminal) {
			// given
			OrderItem item = createItem();
			setStatus(item, terminal);

			// when
			item.awaitDeposit();

			// then
			assertThat(item.getStatus()).isEqualTo(terminal);
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("PAYMENT_PENDING 이면 CANCELED 로 바뀌고 취소 시각이 기록된다")
		void cancelsFromPaymentPending() {
			// given
			OrderItem item = createItem();
			LocalDateTime now = LocalDateTime.now();

			// when
			item.cancel(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(item.getCanceledAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("PAID 면 CANCELED 로 바뀐다")
		void cancelsFromPaid() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PAID);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.cancel(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemStatus.class,
				names = {"CANCELED", "CANCELED_BY_NOPAYMENT", "PURCHASE_CONFIRMED", "RETURNED"})
		@DisplayName("이미 CANCELED 류이면 건드리지 않는다")
		void doesNothingWhenAlreadyTerminal(OrderItemStatus terminal) {
			// given
			OrderItem item = createItem();
			setStatus(item, terminal);
			LocalDateTime beforeCanceledAt = item.getCanceledAt();

			// when
			item.cancel(LocalDateTime.now());

			// then
			assertThat(item.getStatus()).isEqualTo(terminal);
			assertThat(item.getCanceledAt()).isEqualTo(beforeCanceledAt);
		}
	}

	@Nested
	@DisplayName("cancelByNoPayment()")
	class CancelByNoPayment {

		@Test
		@DisplayName("PAYMENT_WAITING 이면 CANCELED_BY_NOPAYMENT 로 바뀌고 취소 시각이 기록된다")
		void cancelsFromPaymentWaiting() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PAYMENT_WAITING);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.cancelByNoPayment(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED_BY_NOPAYMENT);
			assertThat(item.getCanceledAt()).isEqualTo(now);
		}

		@ParameterizedTest
		@EnumSource(value = OrderItemStatus.class,
				names = {"CANCELED", "CANCELED_BY_NOPAYMENT", "PURCHASE_CONFIRMED", "RETURNED"})
		@DisplayName("이미 CANCELED 류이면 건드리지 않는다")
		void doesNothingWhenAlreadyTerminal(OrderItemStatus terminal) {
			// given
			OrderItem item = createItem();
			setStatus(item, terminal);

			// when
			item.cancelByNoPayment(LocalDateTime.now());

			// then
			assertThat(item.getStatus()).isEqualTo(terminal);
		}
	}

	@Nested
	@DisplayName("moveToPreparing()")
	class MoveToPreparing {

		@Test
		@DisplayName("PAID 면 PREPARING 으로 바뀌고 발주확인 시각이 기록된다")
		void movesFromPaid() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PAID);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.moveToPreparing(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PREPARING);
			assertThat(item.getPreparedAt()).isEqualTo(now);
		}
	}

	@Nested
	@DisplayName("moveToShipping()")
	class MoveToShipping {

		@Test
		@DisplayName("PREPARING 이면 SHIPPING 으로 바뀌고 발송 시각이 기록된다")
		void movesFromPreparing() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PREPARING);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.moveToShipping(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
			assertThat(item.getShippedAt()).isEqualTo(now);
		}

		@Test
		@DisplayName("PAID 여도 SHIPPING 으로 바뀐다")
		void movesFromPaid() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.PAID);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.moveToShipping(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
		}
	}

	@Nested
	@DisplayName("moveToDelivered()")
	class MoveToDelivered {

		@Test
		@DisplayName("SHIPPING 이면 DELIVERED 로 바뀌고 배송완료 시각이 기록된다")
		void movesFromShipping() {
			// given
			OrderItem item = createItem();
			setStatus(item, OrderItemStatus.SHIPPING);
			LocalDateTime now = LocalDateTime.now();

			// when
			item.moveToDelivered(now);

			// then
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
			assertThat(item.getDeliveredAt()).isEqualTo(now);
		}
	}

	@Nested
	@DisplayName("applyDiscountShare()")
	class ApplyDiscountShare {

		@Test
		@DisplayName("전달한 값으로 discountShare 를 바꾼다")
		void changesDiscountShare() {
			// given
			OrderItem item = createItem();

			// when
			item.applyDiscountShare(new BigDecimal("1500"));

			// then
			assertThat(item.getDiscountShare()).isEqualByComparingTo("1500");
		}
	}
}
