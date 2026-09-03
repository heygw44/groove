package com.groove.cart.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.CartFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class CartItemTest {

	private final Member member = MemberFixture.create();
	private final Cart cart = CartFixture.createCart(member);
	private final Artist artist = ArtistFixture.create();
	private final Product product = ProductFixture.create(artist);

	@Nested
	@DisplayName("addQuantity()")
	class AddQuantity {

		@ParameterizedTest
		@DisplayName("합산 수량이 최대치 이내면 수량을 더한다")
		@CsvSource({"9, 1, 10", "1, 1, 2", "5, 4, 9"})
		void addsUpWithinMaxQuantity(int initial, int amount, int expected) {
			// given
			CartItem cartItem = CartFixture.createItem(cart, product, initial);

			// when
			cartItem.addQuantity(amount);

			// then
			assertThat(cartItem.getQuantity()).isEqualTo(expected);
		}

		@ParameterizedTest
		@DisplayName("합산 수량이 최대치를 넘으면 CART_QUANTITY_EXCEEDED 예외를 던진다")
		@CsvSource({"9, 2", "10, 1", "5, 6"})
		void throwsWhenSumExceedsMaxQuantity(int initial, int amount) {
			// given
			CartItem cartItem = CartFixture.createItem(cart, product, initial);

			// when & then
			assertThatThrownBy(() -> cartItem.addQuantity(amount))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CART_QUANTITY_EXCEEDED);
		}
	}

	@Nested
	@DisplayName("changeQuantity()")
	class ChangeQuantity {

		@Test
		@DisplayName("1~10 범위면 수량을 변경한다")
		void changesQuantityWithinRange() {
			// given
			CartItem cartItem = CartFixture.createItem(cart, product, 1);

			// when
			cartItem.changeQuantity(CartItem.MAX_QUANTITY);

			// then
			assertThat(cartItem.getQuantity()).isEqualTo(CartItem.MAX_QUANTITY);
		}

		@ParameterizedTest
		@DisplayName("0 이하면 COMMON_INVALID_INPUT 예외를 던진다")
		@CsvSource({"0", "-1"})
		void throwsWhenNotPositive(int quantity) {
			// given
			CartItem cartItem = CartFixture.createItem(cart, product, 1);

			// when & then
			assertThatThrownBy(() -> cartItem.changeQuantity(quantity))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("최대치를 넘으면 CART_QUANTITY_EXCEEDED 예외를 던진다")
		void throwsWhenExceedsMaxQuantity() {
			// given
			CartItem cartItem = CartFixture.createItem(cart, product, 1);

			// when & then
			assertThatThrownBy(() -> cartItem.changeQuantity(CartItem.MAX_QUANTITY + 1))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CART_QUANTITY_EXCEEDED);
		}
	}
}
