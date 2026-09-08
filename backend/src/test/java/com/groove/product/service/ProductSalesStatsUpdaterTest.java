package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductSalesStatsUpdaterTest {

	@Mock
	ProductRepository productRepository;

	ProductSalesStatsUpdater updater;

	Member member;

	@BeforeEach
	void setUp() {
		updater = new ProductSalesStatsUpdater(productRepository);
		member = MemberFixture.withId(MemberFixture.create(), 1L);
	}

	@Nested
	@DisplayName("refreshFor()")
	class RefreshFor {

		@Test
		@DisplayName("같은 상품이 여러 줄에 담겨도 중복 없이 id 오름차순으로 넘긴다")
		void passesDistinctProductIdsInAscendingOrder() {
			// given
			Artist artist = ArtistFixture.withId(1L);
			Product productA = ProductFixture.withId(ProductFixture.create(artist), 5L);
			Product productB = ProductFixture.withId(ProductFixture.create(artist), 3L);
			Order order = OrderFixture.create(member);
			order.addItem(productA, 1);
			order.addItem(productA, 2);
			order.addItem(productB, 1);

			// when
			updater.refreshFor(order);

			// then
			ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
			verify(productRepository).refreshSoldQuantities(captor.capture());
			assertThat(captor.getValue()).containsExactly(3L, 5L);
		}

		@Test
		@DisplayName("주문에 항목이 없으면 리포지토리를 호출하지 않는다")
		void skipsRepositoryCallWhenOrderHasNoItems() {
			// given
			Order order = OrderFixture.create(member);

			// when
			updater.refreshFor(order);

			// then
			verify(productRepository, never()).refreshSoldQuantities(any());
		}
	}
}
