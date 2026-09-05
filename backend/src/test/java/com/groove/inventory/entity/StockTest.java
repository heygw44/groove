package com.groove.inventory.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;

class StockTest {

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("초기 수량이 음수면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenInitialQuantityNegative() {
			// given
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);

			// when & then
			assertThatThrownBy(() -> Stock.create(product, -1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("초기 수량이 0이면 판매중 상품을 품절로 전환한다")
		void marksProductSoldOutWhenInitialQuantityZero() {
			// given
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);

			// when
			Stock.create(product, 0);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("초기 수량이 0보다 크면 상품 상태를 유지한다")
		void keepsProductStatusWhenInitialQuantityPositive() {
			// given
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);

			// when
			Stock stock = Stock.create(product, 5);

			// then
			assertThat(stock.getQuantity()).isEqualTo(5);
			assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
		}
	}

	@Nested
	@DisplayName("increase()")
	class Increase {

		@Test
		@DisplayName("양수를 더하면 수량이 증가한다")
		void increasesQuantity() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 3);

			// when
			stock.increase(2);

			// then
			assertThat(stock.getQuantity()).isEqualTo(5);
		}

		@Test
		@DisplayName("0 이하를 더하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenAmountNotPositive() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 3);

			// when & then
			assertThatThrownBy(() -> stock.increase(0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("품절 상품에 재입고하면 판매중으로 전환한다")
		void resumesProductWhenRestocked() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 0);

			// when
			stock.increase(1);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
		}

		@Test
		@DisplayName("숨김 상품에 재입고해도 상태를 건드리지 않는다")
		void keepsHiddenProductUntouchedWhenRestocked() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 0);
			product.hide();

			// when
			stock.increase(1);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
		}
	}

	@Nested
	@DisplayName("decrease()")
	class Decrease {

		@Test
		@DisplayName("보유 수량 이하를 차감하면 수량이 감소한다")
		void decreasesQuantity() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 5);

			// when
			stock.decrease(2);

			// then
			assertThat(stock.getQuantity()).isEqualTo(3);
		}

		@Test
		@DisplayName("0 이하를 차감하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenAmountNotPositive() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 5);

			// when & then
			assertThatThrownBy(() -> stock.decrease(-1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("보유 수량보다 많이 차감하면 STOCK_INSUFFICIENT 예외를 던진다")
		void throwsWhenInsufficient() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 1);

			// when & then
			assertThatThrownBy(() -> stock.decrease(2))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
		}

		@Test
		@DisplayName("차감 후 수량이 0이 되면 판매중 상품을 품절로 전환한다")
		void marksProductSoldOutWhenDepleted() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 1);

			// when
			stock.decrease(1);

			// then
			assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		}
	}

	@Nested
	@DisplayName("replaceQuantity()")
	class ReplaceQuantity {

		@Test
		@DisplayName("음수로 지정하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenNegative() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 5);

			// when & then
			assertThatThrownBy(() -> stock.replaceQuantity(-1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("0으로 지정하면 수량을 0으로 바꾸고 상품을 품절로 전환한다")
		void setsToZeroAndMarksSoldOut() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 5);

			// when
			stock.replaceQuantity(0);

			// then
			assertThat(stock.getQuantity()).isEqualTo(0);
			assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("양수로 지정하면 수량을 그 값으로 바꾼다")
		void setsToGivenPositiveValue() {
			// given
			Product product = ProductFixture.create(ArtistFixture.create());
			Stock stock = Stock.create(product, 5);

			// when
			stock.replaceQuantity(20);

			// then
			assertThat(stock.getQuantity()).isEqualTo(20);
		}
	}
}
