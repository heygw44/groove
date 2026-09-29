package com.groove.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.OrderFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class DiscountAllocatorTest {

	private final Member member = MemberFixture.create();
	private final Artist artist = ArtistFixture.create();

	@Nested
	@DisplayName("allocate()")
	class Allocate {

		@Test
		@DisplayName("할인이 0이면 모든 상품의 배분액이 0이다")
		void allocatesZeroWhenDiscountIsZero() {
			// given
			Order order = OrderFixture.create(member);
			order.addItem(ProductFixture.create(artist, "A", new BigDecimal("30000")), 1);
			order.addItem(ProductFixture.create(artist, "B", new BigDecimal("20000")), 1);

			// when
			DiscountAllocator.allocate(order.getItems(), BigDecimal.ZERO);

			// then
			assertThat(order.getItems()).allSatisfy(item ->
					assertThat(item.getDiscountShare()).isEqualByComparingTo(BigDecimal.ZERO));
		}

		@Test
		@DisplayName("상품이 하나면 할인액 전체가 그 상품에 배분된다")
		void allocatesWholeDiscountForSingleItem() {
			// given
			Order order = OrderFixture.create(member);
			order.addItem(ProductFixture.create(artist, "A", new BigDecimal("45000")), 1);

			// when
			DiscountAllocator.allocate(order.getItems(), new BigDecimal("5000"));

			// then
			assertThat(order.getItems().get(0).getDiscountShare()).isEqualByComparingTo("5000");
		}

		@Test
		@DisplayName("라인 금액 비율로 나누고 원 미만은 버린다")
		void allocatesProportionallyAndTruncatesFraction() {
			// given — 라인 금액 30000:20000 = 3:2, 할인 1000원을 나누면 600/400으로 나머지가 없다
			Order order = OrderFixture.create(member);
			order.addItem(ProductFixture.create(artist, "A", new BigDecimal("30000")), 1);
			order.addItem(ProductFixture.create(artist, "B", new BigDecimal("20000")), 1);

			// when
			DiscountAllocator.allocate(order.getItems(), new BigDecimal("1000"));

			// then
			assertThat(order.getItems().get(0).getDiscountShare()).isEqualByComparingTo("600");
			assertThat(order.getItems().get(1).getDiscountShare()).isEqualByComparingTo("400");
		}

		@Test
		@DisplayName("배분 후 남는 원 단위는 라인 금액이 가장 큰 상품에 더한다")
		void addsRemainderToLargestLineAmount() {
			// given — 라인 금액 30000:20000:10000, 총 60000원 중 100원 할인을 나누면
			// 각각 FLOOR(100*30000/60000)=50, FLOOR(100*20000/60000)=33, FLOOR(100*10000/60000)=16 이고
			// 합이 99라 나머지 1원이 라인 금액이 가장 큰 A(30000)에 더해진다
			Order order = OrderFixture.create(member);
			order.addItem(ProductFixture.create(artist, "A", new BigDecimal("30000")), 1);
			order.addItem(ProductFixture.create(artist, "B", new BigDecimal("20000")), 1);
			order.addItem(ProductFixture.create(artist, "C", new BigDecimal("10000")), 1);

			// when
			DiscountAllocator.allocate(order.getItems(), new BigDecimal("100"));

			// then
			assertThat(order.getItems().get(0).getDiscountShare()).isEqualByComparingTo("51");
			assertThat(order.getItems().get(1).getDiscountShare()).isEqualByComparingTo("33");
			assertThat(order.getItems().get(2).getDiscountShare()).isEqualByComparingTo("16");
		}

		@Test
		@DisplayName("라인 금액이 동률이면 먼저 추가된(상품주문번호가 앞선) 상품에 나머지를 더한다")
		void addsRemainderToEarlierItemWhenTied() {
			// given — 라인 금액이 같은 두 상품, 총 40000원 중 3원 할인을 나누면
			// 각각 FLOOR(3*20000/40000)=1 이라 합이 2, 나머지 1원은 먼저 추가된(순번 -01) 상품에 더한다
			Order order = OrderFixture.create(member);
			order.addItem(ProductFixture.create(artist, "A", new BigDecimal("20000")), 1);
			order.addItem(ProductFixture.create(artist, "B", new BigDecimal("20000")), 1);

			// when
			DiscountAllocator.allocate(order.getItems(), new BigDecimal("3"));

			// then
			assertThat(order.getItems().get(0).getDiscountShare()).isEqualByComparingTo("2");
			assertThat(order.getItems().get(1).getDiscountShare()).isEqualByComparingTo("1");
		}

		@Test
		@DisplayName("상품이 없으면 아무 것도 하지 않는다")
		void doesNothingWhenItemsEmpty() {
			// given
			Order order = OrderFixture.create(member);

			// when & then — 예외 없이 끝난다
			DiscountAllocator.allocate(order.getItems(), new BigDecimal("1000"));
		}
	}
}
