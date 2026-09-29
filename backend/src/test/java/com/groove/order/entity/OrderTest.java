package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class OrderTest {

	private final Member member = MemberFixture.create();
	private final Artist artist = ArtistFixture.create();

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("생성하면 PENDING 상태이고 금액은 0, 배송지는 그대로 보존된다")
		void createsWithPendingStatusAndZeroAmounts() {
			// given
			ShippingAddress shippingAddress = OrderFixture.shippingAddress();
			LocalDateTime now = LocalDateTime.now();

			// when
			Order order = Order.create("20260903-TESTAB12", member, shippingAddress, OrderSource.CART, now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(order.getFinalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(order.getShippingAddress()).isEqualTo(shippingAddress);
			assertThat(order.getOrderSource()).isEqualTo(OrderSource.CART);
			assertThat(order.isPlaced()).isFalse();
			LocalDateTime expectedExpiresAt = now.plusMinutes(Order.PENDING_EXPIRATION_MINUTES);
			assertThat(order.getExpiresAt()).isEqualTo(expectedExpiresAt);
		}
	}

	@Nested
	@DisplayName("addItem()")
	class AddItem {

		@Test
		@DisplayName("항목을 추가하면 상품명과 가격이 스냅샷으로 복사된다")
		void copiesProductNameAndPriceAsSnapshot() {
			// given
			Order order = OrderFixture.create(member);
			Product product = ProductFixture.create(artist, "Kind of Blue", new BigDecimal("45000"));

			// when
			order.addItem(product, 2);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getProductName()).isEqualTo(product.getTitle());
			assertThat(item.getProductPrice()).isEqualByComparingTo(product.getPrice());
			assertThat(item.getQuantity()).isEqualTo(2);
		}

		@Test
		@DisplayName("두 항목을 추가하면 총액은 각 항목 금액의 합이 된다")
		void sumsLineAmountsOfAllItems() {
			// given
			Order order = OrderFixture.create(member);
			Product first = ProductFixture.create(artist, "Kind of Blue", new BigDecimal("45000"));
			Product second = ProductFixture.create(artist, "A Love Supreme", new BigDecimal("50000"));

			// when
			order.addItem(first, 2);
			order.addItem(second, 1);

			// then
			BigDecimal expected = new BigDecimal("45000").multiply(BigDecimal.valueOf(2))
					.add(new BigDecimal("50000"));
			assertThat(order.getTotalAmount()).isEqualByComparingTo(expected);
			assertThat(order.getFinalAmount()).isEqualByComparingTo(expected);
		}

		@Test
		@DisplayName("수량이 0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenQuantityIsNotPositive() {
			// given
			Order order = OrderFixture.create(member);
			Product product = ProductFixture.create(artist);

			// when & then
			assertThatThrownBy(() -> order.addItem(product, 0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("applyCoupon()")
	class ApplyCoupon {

		@Test
		@DisplayName("적용하면 할인 금액이 반영되고 최종 금액이 재계산된다")
		void appliesDiscountAndRecalculatesFinalAmount() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue",
					new BigDecimal("45000")), 1);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000",
					new BigDecimal("5000")));

			// when
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));

			// then
			assertThat(order.getMemberCoupon()).isEqualTo(memberCoupon);
			assertThat(order.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("5000"));
			assertThat(order.getFinalAmount()).isEqualByComparingTo(new BigDecimal("40000"));
		}

		@Test
		@DisplayName("항목이 없으면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenNoItems() {
			// given
			Order order = OrderFixture.create(member);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000",
					new BigDecimal("5000")));

			// when & then
			assertThatThrownBy(() -> order.applyCoupon(memberCoupon, new BigDecimal("5000")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("할인 금액이 음수면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenDiscountAmountNegative() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000",
					new BigDecimal("5000")));

			// when & then
			assertThatThrownBy(() -> order.applyCoupon(memberCoupon, new BigDecimal("-1")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("할인 금액이 총액을 초과하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenDiscountAmountExceedsTotalAmount() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue",
					new BigDecimal("10000")), 1);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000",
					new BigDecimal("5000")));

			// when & then
			assertThatThrownBy(() -> order.applyCoupon(memberCoupon, new BigDecimal("10001")))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("적용된 이후 addItem 을 호출하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenAddItemAfterCouponApplied() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist, "Kind of Blue",
					new BigDecimal("45000")), 1);
			MemberCoupon memberCoupon = MemberCouponFixture.create(member, CouponFixture.fixed("FIXED5000",
					new BigDecimal("5000")));
			order.applyCoupon(memberCoupon, new BigDecimal("5000"));

			// when & then
			assertThatThrownBy(() -> order.addItem(ProductFixture.create(artist), 1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}
	}

	@Nested
	@DisplayName("markPaid()")
	class MarkPaid {

		@Test
		@DisplayName("PENDING 이면 PAID 로 바뀐다")
		void changesStatusToPaidWhenPending() {
			// given
			Order order = OrderFixture.create(member);

			// when
			order.markPaid();

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("상품주문도 PAID 로 같이 바뀐다")
		void changesItemStatusToPaidToo() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);

			// when
			order.markPaid();

			// then
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PAID);
		}

		@Test
		@DisplayName("이미 PAID 면 ORDER_ALREADY_PAID 예외를 던진다")
		void throwsAlreadyPaidWhenPaid() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();

			// when & then
			assertThatThrownBy(order::markPaid)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class,
				names = {"PREPARING", "SHIPPED", "DELIVERED", "CANCELED", "REFUNDED"})
		@DisplayName("PENDING·PAID 가 아니면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsInvalidStatusForOtherStatuses(OrderStatus status) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", status);

			// when & then
			assertThatThrownBy(order::markPaid)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("awaitDeposit()")
	class AwaitDeposit {

		@Test
		@DisplayName("PENDING 이면 상품주문이 PAYMENT_WAITING 으로 바뀌고 주문 상태는 그대로다")
		void movesItemsToPaymentWaitingWhenPending() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);

			// when
			order.awaitDeposit();

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PAYMENT_WAITING);
		}

		@Test
		@DisplayName("PENDING 이 아니면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotPending() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();

			// when & then
			assertThatThrownBy(order::awaitDeposit)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("cancel()")
	class Cancel {

		@Test
		@DisplayName("PENDING 이면 CANCELED 로 바뀌고 취소 시각·사유가 기록된다")
		void cancelsAndRecordsReasonWhenPending() {
			// given
			Order order = OrderFixture.create(member);

			// when
			order.cancel("고객 변심");

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isNotNull();
			assertThat(order.getCancelReason()).isEqualTo("고객 변심");
		}

		@Test
		@DisplayName("상품주문도 CANCELED 로 같이 바뀐다")
		void changesItemStatusToCanceledToo() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);

			// when
			order.cancel("고객 변심");

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(item.getCanceledAt()).isNotNull();
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class,
				names = {"PAID", "PREPARING", "SHIPPED", "DELIVERED", "CANCELED", "REFUNDED"})
		@DisplayName("취소 불가 상태면 ORDER_CANNOT_CANCEL 예외를 던진다")
		void throwsCannotCancelForNonCancelableStatuses(OrderStatus status) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", status);

			// when & then
			assertThatThrownBy(() -> order.cancel("고객 변심"))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
		}
	}

	@Nested
	@DisplayName("requestCancel()")
	class RequestCancel {

		@Test
		@DisplayName("회원 취소면 PAID 상태에서 사유만 기록한다")
		void recordsReasonWithoutChangingStatusForMember() {
			// given
			Order order = OrderFixture.markPaid(OrderFixture.create(member));

			// when
			order.requestCancel("고객 변심", false);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getCancelReason()).isEqualTo("고객 변심");
			assertThat(order.getCanceledAt()).isNull();
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING"})
		@DisplayName("관리자 취소면 취소 가능한 상태에서 관리자 사유만 기록한다")
		void recordsAdminReasonWithoutChangingStatus(OrderStatus status) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", status);

			// when
			order.requestCancel(null, true);

			// then
			assertThat(order.getStatus()).isEqualTo(status);
			assertThat(order.getCancelReason()).isEqualTo("관리자 취소");
		}

		@Test
		@DisplayName("회원 취소가 PAID 상태가 아니면 ORDER_CANNOT_CANCEL 예외를 던진다")
		void throwsWhenMemberOrderIsNotPaid() {
			// given
			Order order = OrderFixture.create(member);

			// when & then
			assertThatThrownBy(() -> order.requestCancel("고객 변심", false))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
		}

		@Test
		@DisplayName("관리자 취소가 허용되지 않은 상태면 ORDER_INVALID_STATUS_TRANSITION 예외를 던진다")
		void throwsWhenAdminTransitionIsNotAllowed() {
			// given
			Order order = OrderFixture.markDelivered(OrderFixture.create(member));

			// when & then
			assertThatThrownBy(() -> order.requestCancel(null, true))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
		}
	}

	@Nested
	@DisplayName("completeCancel()")
	class CompleteCancel {

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING"})
		@DisplayName("PAID 또는 PREPARING 이면 CANCELED 로 바뀌고 취소 시각을 기록한다")
		void completesCancelForPaidOrderStatuses(OrderStatus status) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", status);
			order.requestCancel("고객 변심", status == OrderStatus.PREPARING);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 13, 10, 30);

			// when
			order.completeCancel(canceledAt);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isEqualTo(canceledAt);
			assertThat(order.getCancelReason()).isNotNull();
		}

		@Test
		@DisplayName("PAID 또는 PREPARING 이 아니면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsForInvalidStatus() {
			// given
			Order order = OrderFixture.create(member);

			// when & then
			assertThatThrownBy(() -> order.completeCancel(LocalDateTime.of(2026, 9, 13, 10, 30)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("상품주문도 CANCELED 로 같이 바뀐다")
		void changesItemStatusToCanceledToo() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			order.markPaid();
			order.requestCancel("고객 변심", false);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 13, 10, 30);

			// when
			order.completeCancel(canceledAt);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED);
			assertThat(item.getCanceledAt()).isEqualTo(canceledAt);
		}
	}

	@Nested
	@DisplayName("withdrawCancelRequest()")
	class WithdrawCancelRequest {

		@Test
		@DisplayName("취소 요청을 철회하면 취소 사유를 지운다")
		void clearsCancelReason() {
			// given
			Order order = OrderFixture.markPaid(OrderFixture.create(member));
			order.requestCancel("고객 변심", false);

			// when
			order.withdrawCancelRequest();

			// then
			assertThat(order.getCancelReason()).isNull();
		}
	}

	@Nested
	@DisplayName("changeStatus()")
	class ChangeStatus {

		@ParameterizedTest
		@CsvSource({
			"PAID, PREPARING",
			"PREPARING, SHIPPED",
			"SHIPPED, DELIVERED"
		})
		@DisplayName("허용된 전이면 상태가 바뀐다")
		void changesStatusForAllowedTransition(OrderStatus from, OrderStatus to) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", from);

			// when
			order.changeStatus(to);

			// then
			assertThat(order.getStatus()).isEqualTo(to);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING"})
		@DisplayName("CANCELED 로 전이하면 취소 시각과 사유가 기록된다")
		void recordsCanceledAtAndReasonWhenTransitioningToCanceled(OrderStatus from) {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", from);

			// when
			order.changeStatus(OrderStatus.CANCELED);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isNotNull();
			assertThat(order.getCancelReason()).isEqualTo("관리자 취소");
		}

		@Test
		@DisplayName("허용되지 않은 전이면 ORDER_INVALID_STATUS_TRANSITION 예외를 던진다")
		void throwsWhenTransitionNotAllowed() {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", OrderStatus.DELIVERED);

			// when & then
			assertThatThrownBy(() -> order.changeStatus(OrderStatus.SHIPPED))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS_TRANSITION);
		}

		@Test
		@DisplayName("PREPARING 으로 전이하면 상품주문도 PREPARING 으로 바뀐다")
		void movesItemToPreparing() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.PAID);

			// when
			order.changeStatus(OrderStatus.PREPARING);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PREPARING);
			assertThat(item.getPreparedAt()).isNotNull();
		}

		@Test
		@DisplayName("SHIPPED 로 전이하면 상품주문은 SHIPPING 으로 바뀐다")
		void movesItemToShipping() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", OrderStatus.PREPARING);
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.PREPARING);

			// when
			order.changeStatus(OrderStatus.SHIPPED);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
			assertThat(item.getShippedAt()).isNotNull();
		}

		@Test
		@DisplayName("DELIVERED 로 전이하면 상품주문도 DELIVERED 로 바뀐다")
		void movesItemToDelivered() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", OrderStatus.SHIPPED);
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.SHIPPING);

			// when
			order.changeStatus(OrderStatus.DELIVERED);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
			assertThat(item.getDeliveredAt()).isNotNull();
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "PREPARING"})
		@DisplayName("CANCELED 로 전이하면 상품주문도 CANCELED 로 바뀐다")
		void movesItemToCanceled(OrderStatus from) {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", from);
			ReflectionTestUtils.setField(order.getItems().get(0), "status",
					from == OrderStatus.PAID ? OrderItemStatus.PAID : OrderItemStatus.PREPARING);

			// when
			order.changeStatus(OrderStatus.CANCELED);

			// then
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		}
	}

	@Nested
	@DisplayName("expire()")
	class Expire {

		@Test
		@DisplayName("PENDING 이면 CANCELED 로 바뀌고 취소 시각·사유가 기록된다")
		void cancelsWithExpiredReasonWhenPending() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime now = LocalDateTime.now();

			// when
			order.expire(now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isEqualTo(now);
			assertThat(order.getCancelReason()).isEqualTo(Order.EXPIRED_CANCEL_REASON);
		}

		@Test
		@DisplayName("PAID 면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenPaid() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();

			// when & then
			assertThatThrownBy(() -> order.expire(LocalDateTime.now()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("CANCELED 면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenAlreadyCanceled() {
			// given
			Order order = OrderFixture.create(member);
			ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELED);

			// when & then
			assertThatThrownBy(() -> order.expire(LocalDateTime.now()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("상품주문이 PAYMENT_PENDING 이면 CANCELED 로 바뀐다(10분 만료)")
		void movesPaymentPendingItemToCanceled() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			LocalDateTime now = LocalDateTime.now();

			// when
			order.expire(now);

			// then
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		}

		@Test
		@DisplayName("상품주문이 PAYMENT_WAITING 이면 CANCELED_BY_NOPAYMENT 로 바뀐다(가상계좌 입금기한 만료)")
		void movesPaymentWaitingItemToCanceledByNoPayment() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order.getItems().get(0), "status", OrderItemStatus.PAYMENT_WAITING);
			LocalDateTime now = LocalDateTime.now();

			// when
			order.expire(now);

			// then
			OrderItem item = order.getItems().get(0);
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.CANCELED_BY_NOPAYMENT);
			assertThat(item.getCanceledAt()).isEqualTo(now);
		}
	}

	@Nested
	@DisplayName("supersede()")
	class Supersede {

		@Test
		@DisplayName("PENDING 이면 CANCELED 로 바뀌고 SUPERSEDED 사유가 기록된다")
		void cancelsWithSupersededReasonWhenPending() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime now = LocalDateTime.now();

			// when
			order.supersede(now);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
			assertThat(order.getCanceledAt()).isEqualTo(now);
			assertThat(order.getCancelReason()).isEqualTo(Order.SUPERSEDED_CANCEL_REASON);
		}

		@Test
		@DisplayName("PAID 면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenPaid() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();

			// when & then
			assertThatThrownBy(() -> order.supersede(LocalDateTime.now()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("상품주문도 CANCELED 로 같이 바뀐다")
		void changesItemStatusToCanceledToo() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			LocalDateTime now = LocalDateTime.now();

			// when
			order.supersede(now);

			// then
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.CANCELED);
		}
	}

	@Nested
	@DisplayName("changeShippingAddress()")
	class ChangeShippingAddress {

		@Test
		@DisplayName("확정 전 PENDING 주문이면 배송지가 바뀐다")
		void changesAddressWhenPendingAndNotPlaced() {
			// given
			Order order = OrderFixture.create(member);
			ShippingAddress newAddress = ShippingAddress.of("김바이닐", "010-9999-8888", "12345", "서울시 서초구 1",
					null);

			// when
			order.changeShippingAddress(newAddress);

			// then
			assertThat(order.getShippingAddress()).isEqualTo(newAddress);
		}

		@Test
		@DisplayName("이미 확정된 주문이면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenAlreadyPlaced() {
			// given
			Order order = OrderFixture.create(member);
			order.place(LocalDateTime.now());
			ShippingAddress newAddress = ShippingAddress.of("김바이닐", "010-9999-8888", "12345", "서울시 서초구 1",
					null);

			// when & then
			assertThatThrownBy(() -> order.changeShippingAddress(newAddress))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}

		@Test
		@DisplayName("PENDING 이 아니면 ORDER_INVALID_STATUS 예외를 던진다")
		void throwsWhenNotPending() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();
			ShippingAddress newAddress = ShippingAddress.of("김바이닐", "010-9999-8888", "12345", "서울시 서초구 1",
					null);

			// when & then
			assertThatThrownBy(() -> order.changeShippingAddress(newAddress))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_INVALID_STATUS);
		}
	}

	@Nested
	@DisplayName("isExpired()")
	class IsExpired {

		@Test
		@DisplayName("만료 시각 이전이면 false 를 반환한다")
		void returnsFalseBeforeExpiresAt() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime now = order.getExpiresAt().minusMinutes(1);

			// when & then
			assertThat(order.isExpired(now)).isFalse();
		}

		@Test
		@DisplayName("만료 시각과 정확히 같으면 true 를 반환한다")
		void returnsTrueAtExactExpiresAt() {
			// given
			Order order = OrderFixture.create(member);

			// when & then
			assertThat(order.isExpired(order.getExpiresAt())).isTrue();
		}

		@Test
		@DisplayName("만료 시각 이후면 true 를 반환한다")
		void returnsTrueAfterExpiresAt() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime now = order.getExpiresAt().plusMinutes(1);

			// when & then
			assertThat(order.isExpired(now)).isTrue();
		}

		@Test
		@DisplayName("PAID 면 만료 시각이 지났어도 false 를 반환한다")
		void returnsFalseWhenPaidEvenIfPastExpiresAt() {
			// given
			Order order = OrderFixture.create(member);
			order.markPaid();
			LocalDateTime now = order.getExpiresAt().plusMinutes(1);

			// when & then
			assertThat(order.isExpired(now)).isFalse();
		}
	}

	@Nested
	@DisplayName("place()")
	class Place {

		@Test
		@DisplayName("처음 호출하면 확정 시각이 기록되고 isPlaced() 는 true 다")
		void recordsPlacedAtOnFirstCall() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime placedAt = LocalDateTime.of(2026, 9, 28, 10, 0);

			// when
			order.place(placedAt);

			// then
			assertThat(order.getPlacedAt()).isEqualTo(placedAt);
			assertThat(order.isPlaced()).isTrue();
		}

		@Test
		@DisplayName("이미 확정된 주문에 다시 호출해도 최초 확정 시각을 유지한다")
		void keepsFirstPlacedAtOnSecondCall() {
			// given
			Order order = OrderFixture.create(member);
			LocalDateTime firstPlacedAt = LocalDateTime.of(2026, 9, 28, 10, 0);
			order.place(firstPlacedAt);

			// when
			order.place(firstPlacedAt.plusDays(1));

			// then
			assertThat(order.getPlacedAt()).isEqualTo(firstPlacedAt);
		}
	}

	@Nested
	@DisplayName("isPlaced()")
	class IsPlaced {

		@Test
		@DisplayName("생성 직후에는 false 를 반환한다")
		void returnsFalseRightAfterCreate() {
			// given
			Order order = OrderFixture.create(member);

			// when & then
			assertThat(order.isPlaced()).isFalse();
		}
	}

	@Nested
	@DisplayName("alignStatusWithItems()")
	class AlignStatusWithItems {

		@Test
		@DisplayName("허용되는 전이면 상태를 바꾼다")
		void changesStatusWhenTransitionAllowed() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", OrderStatus.PAID);

			// when
			order.alignStatusWithItems(OrderStatus.PREPARING);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
		}

		@Test
		@DisplayName("허용되지 않는 전이면 조용히 무시한다")
		void ignoresDisallowedTransition() {
			// given
			Order order = OrderFixture.createWithItem(member, ProductFixture.create(artist), 1);
			ReflectionTestUtils.setField(order, "status", OrderStatus.PENDING);

			// when
			order.alignStatusWithItems(OrderStatus.DELIVERED);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
		}
	}
}
