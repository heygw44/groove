package com.groove.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.coupon.entity.MemberCoupon;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CouponFixture;
import com.groove.fixture.MemberCouponFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.limited.service.LimitedPurchaseWriter;
import com.groove.limited.service.LimitedRelease;
import com.groove.limited.service.LimitedReleaseSynchronizer;
import com.groove.member.entity.Member;
import com.groove.order.entity.Order;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.service.ProductSalesStatsUpdater;

@ExtendWith(MockitoExtension.class)
class OrderCancelRestorerTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);

	@Mock
	OrderStockService orderStockService;

	@Mock
	LimitedPurchaseWriter limitedPurchaseWriter;

	@Mock
	LimitedReleaseSynchronizer limitedReleaseSynchronizer;

	@Mock
	ProductSalesStatsUpdater productSalesStatsUpdater;

	OrderCancelRestorer restorer;
	Order order;
	MemberCoupon memberCoupon;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-09-13T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		restorer = new OrderCancelRestorer(orderStockService, limitedPurchaseWriter, limitedReleaseSynchronizer,
				productSalesStatsUpdater, clock);
		Member member = MemberFixture.withId(MemberFixture.create(), 1L);
		Artist artist = ArtistFixture.withId(1L);
		Product product = ProductFixture.withId(ProductFixture.create(artist), 10L);
		order = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 100L);
		memberCoupon = MemberCouponFixture.create(member,
				CouponFixture.fixed("CANCEL5000", new BigDecimal("5000")));
		order.applyCoupon(memberCoupon, new BigDecimal("5000"));
		memberCoupon.use(order.getId());
	}

	@Nested
	@DisplayName("restore()")
	class Restore {

		@Test
		@DisplayName("복구 순서를 지키고 한정반 선점을 커밋 후 해제한다")
		void restoresResourcesInOrder() {
			// given
			LimitedRelease release = new LimitedRelease(30L, 1L);
			given(limitedPurchaseWriter.revertByOrder(order.getId(), NOW)).willReturn(Optional.of(release));

			// when
			Optional<LimitedRelease> result = restorer.restore(order, true);

			// then
			InOrder inOrder = Mockito.inOrder(orderStockService, limitedPurchaseWriter, limitedReleaseSynchronizer,
					productSalesStatsUpdater);
			inOrder.verify(orderStockService).restore(order);
			inOrder.verify(limitedPurchaseWriter).revertByOrder(order.getId(), NOW);
			inOrder.verify(limitedReleaseSynchronizer).releaseAfterCommit(release);
			inOrder.verify(productSalesStatsUpdater).refreshFor(order);
			assertThat(memberCoupon.isUsed()).isFalse();
			assertThat(result).contains(release);
		}

		@Test
		@DisplayName("미결제 주문이면 판매량을 재계산하지 않는다")
		void skipsSalesRefreshForUnpaidOrder() {
			// given
			Member member = MemberFixture.withId(MemberFixture.create(), 2L);
			Artist artist = ArtistFixture.withId(2L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 20L);
			Order orderWithoutCoupon = OrderFixture.withId(OrderFixture.createWithItem(member, product, 1), 200L);
			given(limitedPurchaseWriter.revertByOrder(orderWithoutCoupon.getId(), NOW))
					.willReturn(Optional.empty());

			// when
			restorer.restore(orderWithoutCoupon, false);

			// then
			verify(productSalesStatsUpdater, never()).refreshFor(orderWithoutCoupon);
		}
	}
}
