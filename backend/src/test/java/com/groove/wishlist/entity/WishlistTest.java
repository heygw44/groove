package com.groove.wishlist.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class WishlistTest {

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("회원과 상품을 그대로 보관한다")
		void keepsMemberAndProduct() {
			// given
			Member member = MemberFixture.create();
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);

			// when
			Wishlist wishlist = Wishlist.create(member, product);

			// then
			assertThat(wishlist.getMember()).isEqualTo(member);
			assertThat(wishlist.getProduct()).isEqualTo(product);
		}
	}
}
