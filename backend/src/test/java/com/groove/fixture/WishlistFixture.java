package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.product.entity.Product;
import com.groove.wishlist.dto.WishlistAddRequest;
import com.groove.wishlist.dto.WishlistSearchRequest;
import com.groove.wishlist.entity.Wishlist;

public final class WishlistFixture {

	private WishlistFixture() {
	}

	public static Wishlist create(Member member, Product product) {
		return Wishlist.create(member, product);
	}

	public static Wishlist withId(Wishlist wishlist, Long id) {
		ReflectionTestUtils.setField(wishlist, "id", id);
		return wishlist;
	}

	public static WishlistAddRequest addRequest(Long productId) {
		return new WishlistAddRequest(productId);
	}

	public static WishlistSearchRequest searchRequest(Integer page, Integer size) {
		return new WishlistSearchRequest(page, size);
	}
}
