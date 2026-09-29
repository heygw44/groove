package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderStatusAlignerTest {

	private static final Long ORDER_ID = 500L;

	@Mock
	private OrderRepository orderRepository;

	private OrderStatusAligner aligner;

	private Member member;
	private Product product;

	@BeforeEach
	void setUp() {
		aligner = new OrderStatusAligner(orderRepository);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("alignIfAllItemsMatch()")
	class AlignIfAllItemsMatch {

		@Test
		@DisplayName("모든 상품주문이 대상 상태면 Order.status 도 맞춘다")
		void alignsWhenAllItemsMatch() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID, OrderItemStatus.PREPARING);
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

			// when
			aligner.alignIfAllItemsMatch(ORDER_ID, OrderItemStatus.PREPARING, OrderStatus.PREPARING);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
		}

		@Test
		@DisplayName("일부 상품주문만 대상 상태면 그대로 둔다")
		void doesNotAlignWhenSomeItemsDiffer() {
			// given
			Order order = orderWithStatus(OrderStatus.PAID, OrderItemStatus.PAID);
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.of(order));

			// when
			aligner.alignIfAllItemsMatch(ORDER_ID, OrderItemStatus.PREPARING, OrderStatus.PREPARING);

			// then
			assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
		}

		@Test
		@DisplayName("주문을 찾지 못하면 아무 일도 하지 않는다")
		void doesNothingWhenOrderNotFound() {
			// given
			given(orderRepository.findWithItemsById(ORDER_ID)).willReturn(Optional.empty());

			// when & then(예외 없이 조용히 넘어간다)
			aligner.alignIfAllItemsMatch(ORDER_ID, OrderItemStatus.PREPARING, OrderStatus.PREPARING);
		}
	}

	private Order orderWithStatus(OrderStatus orderStatus, OrderItemStatus itemStatus) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		ReflectionTestUtils.setField(order, "status", orderStatus);
		OrderFixture.markItemsStatus(order, itemStatus);
		return order;
	}
}
