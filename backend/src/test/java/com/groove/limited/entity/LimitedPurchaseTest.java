package com.groove.limited.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class LimitedPurchaseTest {

	private final Artist artist = ArtistFixture.withId(1L);
	private final Product product = ProductFixture.withId(ProductFixture.create(artist), 100L);
	private final Member member = MemberFixture.withId(MemberFixture.create(), 1L);

	@Nested
	@DisplayName("create()")
	class Create {

		@ParameterizedTest
		@CsvSource({
			"0, false",
			"1, true",
			"2, true",
			"3, false"
		})
		@DisplayName("수량이 1 이상 회원당 한도 이하면 생성되고 아니면 LIMITED_LIMIT_EXCEEDED 예외를 던진다")
		void validatesQuantityBoundary(int quantity, boolean shouldPass) {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 100, 2);

			// when & then
			if (shouldPass) {
				LimitedPurchase purchase = LimitedPurchase.create(drop, member, null, quantity);
				assertThat(purchase.getQuantity()).isEqualTo(quantity);
			} else {
				assertThatThrownBy(() -> LimitedPurchase.create(drop, member, null, quantity))
						.isInstanceOf(BusinessException.class)
						.extracting("errorCode")
						.isEqualTo(ErrorCode.LIMITED_LIMIT_EXCEEDED);
			}
		}

		@Test
		@DisplayName("생성한 구매 이력은 전달한 드롭·회원을 그대로 참조한다")
		void referencesGivenDropAndMember() {
			// given
			LimitedDrop drop = LimitedDropFixture.scheduled(product, 100, 2);

			// when
			LimitedPurchase purchase = LimitedPurchase.create(drop, member, null, 1);

			// then
			assertThat(purchase.getDrop()).isEqualTo(drop);
			assertThat(purchase.getMember()).isEqualTo(member);
			assertThat(purchase.getOrder()).isNull();
		}
	}
}
