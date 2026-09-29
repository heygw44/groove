package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
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
import com.groove.order.entity.OrderItem;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderAutoDeliverServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final Long ITEM_ID = 900L;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private OrderRepository orderRepository;

	private OrderAutoDeliverService service;

	private Member member;
	private Product product;

	@BeforeEach
	void setUp() {
		service = new OrderAutoDeliverService(orderItemRepository, orderRepository);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("deliver()")
	class Deliver {

		@Test
		@DisplayName("SHIPPING 이고 발송 시각이 cutoff 이전이면 배송완료로 바꾸고 true 를 반환한다")
		void deliversWhenEligible() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(5);
			Order order = itemWithStatus(OrderItemStatus.SHIPPING);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "shippedAt", cutoff.minusDays(1));
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.deliver(ITEM_ID, cutoff, now);

			// then
			assertThat(result).isTrue();
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
		}

		@Test
		@DisplayName("상품주문이 속한 주문을 찾지 못하면 false 를 반환한다")
		void returnsFalseWhenOrderIdNotFound() {
			// given
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.empty());

			// when
			boolean result = service.deliver(ITEM_ID, LocalDateTime.now().minusDays(5), LocalDateTime.now());

			// then
			assertThat(result).isFalse();
			verify(orderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("잠금 뒤 다시 확인했을 때 이미 SHIPPING 이 아니면 false 를 반환한다")
		void returnsFalseWhenNoLongerShippingAfterLock() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(5);
			Order order = itemWithStatus(OrderItemStatus.DELIVERED);
			OrderItem item = order.getItems().get(0);
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.deliver(ITEM_ID, cutoff, now);

			// then
			assertThat(result).isFalse();
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
		}

		@Test
		@DisplayName("발송 시각이 cutoff 이후면 false 를 반환한다")
		void returnsFalseWhenShippedAfterCutoff() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(5);
			Order order = itemWithStatus(OrderItemStatus.SHIPPING);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "shippedAt", cutoff.plusHours(1));
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.deliver(ITEM_ID, cutoff, now);

			// then
			assertThat(result).isFalse();
		}
	}

	private Order itemWithStatus(OrderItemStatus status) {
		Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), ORDER_ID);
		OrderFixture.markItemsStatus(order, status);
		ReflectionTestUtils.setField(order.getItems().get(0), "id", ITEM_ID);
		return order;
	}
}
