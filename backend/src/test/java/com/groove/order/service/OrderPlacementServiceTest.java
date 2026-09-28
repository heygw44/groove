package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.cart.repository.CartItemRepository;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderSource;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

@ExtendWith(MockitoExtension.class)
class OrderPlacementServiceTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	CartItemRepository cartItemRepository;

	OrderPlacementService orderPlacementService;

	Member member;
	Product product;

	@BeforeEach
	void setUp() {
		orderPlacementService = new OrderPlacementService(cartItemRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.withId(1L);
		product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	}

	@Nested
	@DisplayName("place()")
	class Place {

		@Test
		@DisplayName("장바구니 출처 주문이면 확정 시각을 기록하고 담긴 상품을 장바구니에서 지운다")
		void placesAndDeletesCartItemsForCartOrder() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 2), 1L);
			LocalDateTime at = LocalDateTime.of(2026, 9, 28, 10, 0);

			// when
			orderPlacementService.place(order, at);

			// then
			assertThat(order.getPlacedAt()).isEqualTo(at);
			verify(cartItemRepository).deleteByCartMemberIdAndProductIdIn(MEMBER_ID, List.of(product.getId()));
		}

		@Test
		@DisplayName("한정반 출처 주문이면 장바구니를 건드리지 않는다")
		void doesNotDeleteCartItemsForLimitedOrder() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 2L);
			ReflectionTestUtils.setField(order, "orderSource", OrderSource.LIMITED);
			LocalDateTime at = LocalDateTime.of(2026, 9, 28, 10, 0);

			// when
			orderPlacementService.place(order, at);

			// then
			assertThat(order.getPlacedAt()).isEqualTo(at);
			verify(cartItemRepository, never()).deleteByCartMemberIdAndProductIdIn(any(), any());
		}

		@Test
		@DisplayName("직접구매 출처 주문이면 장바구니를 건드리지 않는다")
		void doesNotDeleteCartItemsForDirectOrder() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 3L);
			ReflectionTestUtils.setField(order, "orderSource", OrderSource.DIRECT);
			LocalDateTime at = LocalDateTime.of(2026, 9, 28, 10, 0);

			// when
			orderPlacementService.place(order, at);

			// then
			assertThat(order.getPlacedAt()).isEqualTo(at);
			verify(cartItemRepository, never()).deleteByCartMemberIdAndProductIdIn(any(), any());
		}

		@Test
		@DisplayName("이미 확정된 주문에 다시 호출해도 최초 확정 시각을 유지한다")
		void keepsFirstPlacedAtOnSecondCall() {
			// given
			Order order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 4L);
			LocalDateTime firstAt = LocalDateTime.of(2026, 9, 28, 10, 0);
			orderPlacementService.place(order, firstAt);

			// when
			orderPlacementService.place(order, firstAt.plusDays(1));

			// then
			assertThat(order.getPlacedAt()).isEqualTo(firstAt);
		}
	}
}
