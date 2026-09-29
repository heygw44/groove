package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
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
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderItemConfirmWriterTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;
	private static final Long ORDER_ID = 500L;
	private static final Long ITEM_ID = 900L;

	@Mock
	private OrderRepository orderRepository;

	private OrderItemConfirmWriter writer;

	private Member member;
	private Product product;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		writer = new OrderItemConfirmWriter(orderRepository, clock);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("confirm()")
	class Confirm {

		@Test
		@DisplayName("본인 주문이고 SHIPPING·DELIVERED 면 구매확정한다")
		void confirmsForOwnedShippingOrDeliveredItem() {
			// given
			Order order = ownedOrder(OrderItemStatus.SHIPPING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when
			writer.confirm(MEMBER_ID, ORDER_ID, ITEM_ID);

			// then
			assertThat(order.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PURCHASE_CONFIRMED);
		}

		@Test
		@DisplayName("주문이 없으면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenOrderNotFound() {
			// given
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> writer.confirm(MEMBER_ID, ORDER_ID, ITEM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("타인의 주문이면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			Order order = ownedOrder(OrderItemStatus.SHIPPING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.confirm(OTHER_MEMBER_ID, ORDER_ID, ITEM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("존재하지 않는 상품주문 id 면 ORDER_NOT_FOUND 예외를 던진다")
		void throwsWhenItemNotFound() {
			// given
			Order order = ownedOrder(OrderItemStatus.SHIPPING);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.confirm(MEMBER_ID, ORDER_ID, 12345L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_NOT_FOUND);
		}

		@Test
		@DisplayName("PAID 처럼 발송 전이면 ORDER_CLAIM_NOT_ALLOWED 예외를 던진다")
		void throwsWhenNotConfirmable() {
			// given
			Order order = ownedOrder(OrderItemStatus.PAID);
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderRepository.findWithItemsByIdAndMemberId(ORDER_ID, MEMBER_ID)).willReturn(Optional.of(order));

			// when & then
			assertThatThrownBy(() -> writer.confirm(MEMBER_ID, ORDER_ID, ITEM_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ORDER_CLAIM_NOT_ALLOWED);
		}
	}

	private Order ownedOrder(OrderItemStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		OrderFixture.markItemsStatus(order, status);
		ReflectionTestUtils.setField(order.getItems().get(0), "id", ITEM_ID);
		return order;
	}
}
