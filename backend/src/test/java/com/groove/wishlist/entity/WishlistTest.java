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
			assertThat(wishlist.isAlertEnabled()).isTrue();
		}
	}

	@Nested
	@DisplayName("changeAlert()")
	class ChangeAlert {

		@Test
		@DisplayName("false 로 바꾸면 알림 수신이 꺼진다")
		void disablesAlert() {
			// given
			Member member = MemberFixture.create();
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);
			Wishlist wishlist = Wishlist.create(member, product);

			// when
			wishlist.changeAlert(false);

			// then
			assertThat(wishlist.isAlertEnabled()).isFalse();
		}

		@Test
		@DisplayName("true 로 바꾸면 알림 수신이 켜진다")
		void enablesAlert() {
			// given
			Member member = MemberFixture.create();
			Artist artist = ArtistFixture.create();
			Product product = ProductFixture.create(artist);
			Wishlist wishlist = Wishlist.create(member, product);
			wishlist.changeAlert(false);

			// when
			wishlist.changeAlert(true);

			// then
			assertThat(wishlist.isAlertEnabled()).isTrue();
		}
	}
}
