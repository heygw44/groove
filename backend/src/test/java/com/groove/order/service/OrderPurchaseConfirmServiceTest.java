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
import com.groove.order.entity.OrderItemClaimStatus;
import com.groove.order.entity.OrderItemStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.order.repository.OrderRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderPurchaseConfirmServiceTest {

	private static final Long ORDER_ID = 500L;
	private static final Long ITEM_ID = 900L;

	@Mock
	private OrderItemRepository orderItemRepository;

	@Mock
	private OrderRepository orderRepository;

	private OrderPurchaseConfirmService service;

	private Member member;
	private Product product;

	@BeforeEach
	void setUp() {
		service = new OrderPurchaseConfirmService(orderItemRepository, orderRepository);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("confirm()")
	class Confirm {

		@Test
		@DisplayName("DELIVERED 이고 배송완료 시각이 cutoff 이전이며 클레임이 없으면 구매확정하고 true 를 반환한다")
		void confirmsWhenEligible() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(8);
			Order order = itemWithStatus(OrderItemStatus.DELIVERED);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "deliveredAt", cutoff.minusDays(1));
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.confirm(ITEM_ID, cutoff, now);

			// then
			assertThat(result).isTrue();
			assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PURCHASE_CONFIRMED);
		}

		@Test
		@DisplayName("상품주문이 속한 주문을 찾지 못하면 false 를 반환한다")
		void returnsFalseWhenOrderIdNotFound() {
			// given
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.empty());

			// when
			boolean result = service.confirm(ITEM_ID, LocalDateTime.now().minusDays(8), LocalDateTime.now());

			// then
			assertThat(result).isFalse();
			verify(orderRepository, never()).findByIdForUpdate(any());
		}

		@Test
		@DisplayName("진행 중인 반품 클레임이 있으면 false 를 반환한다")
		void returnsFalseWhenClaimInProgress() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(8);
			Order order = itemWithStatus(OrderItemStatus.DELIVERED);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "deliveredAt", cutoff.minusDays(1));
			ReflectionTestUtils.setField(item, "claimStatus", OrderItemClaimStatus.RETURN_REQUEST);
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.confirm(ITEM_ID, cutoff, now);

			// then
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("배송완료 시각이 cutoff 이후면 false 를 반환한다")
		void returnsFalseWhenDeliveredAfterCutoff() {
			// given
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime cutoff = now.minusDays(8);
			Order order = itemWithStatus(OrderItemStatus.DELIVERED);
			OrderItem item = order.getItems().get(0);
			ReflectionTestUtils.setField(item, "deliveredAt", cutoff.plusHours(1));
			given(orderItemRepository.findOrderIdById(ITEM_ID)).willReturn(Optional.of(ORDER_ID));
			given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
			given(orderItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));

			// when
			boolean result = service.confirm(ITEM_ID, cutoff, now);

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
