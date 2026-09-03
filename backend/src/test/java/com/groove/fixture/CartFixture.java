package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.cart.dto.CartItemAddRequest;
import com.groove.cart.dto.CartItemQuantityUpdateRequest;
import com.groove.cart.entity.Cart;
import com.groove.cart.entity.CartItem;
import com.groove.member.entity.Member;
import com.groove.product.entity.Product;

public final class CartFixture {

	private static final int DEFAULT_QUANTITY = 1;

	private CartFixture() {
	}

	public static Cart createCart(Member member) {
		return Cart.create(member);
	}

	public static CartItem createItem(Cart cart, Product product) {
		return createItem(cart, product, DEFAULT_QUANTITY);
	}

	public static CartItem createItem(Cart cart, Product product, int quantity) {
		return CartItem.create(cart, product, quantity);
	}

	public static Cart withId(Cart cart, Long id) {
		ReflectionTestUtils.setField(cart, "id", id);
		return cart;
	}

	public static CartItem withId(CartItem cartItem, Long id) {
		ReflectionTestUtils.setField(cartItem, "id", id);
		return cartItem;
	}

	public static CartItemAddRequest addRequest(Long productId, int quantity) {
		return new CartItemAddRequest(productId, quantity);
	}

	public static CartItemQuantityUpdateRequest quantityRequest(int quantity) {
		return new CartItemQuantityUpdateRequest(quantity);
	}
}
