package com.groove.review.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.member.entity.Member;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;

class ReviewTest {

	private final Artist artist = ArtistFixture.create();
	private final Product product = ProductFixture.create(artist);
	private final Member member = MemberFixture.withId(MemberFixture.create(), 1L);

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("평점·제목·내용을 새 값으로 교체한다")
		void replacesRatingTitleAndContent() {
			// given
			Review review = ReviewFixture.create(product, member);

			// when
			review.update(3, "생각보다 별로예요", "재구매 의사 없습니다.");

			// then
			assertThat(review.getRating()).isEqualTo(3);
			assertThat(review.getTitle()).isEqualTo("생각보다 별로예요");
			assertThat(review.getContent()).isEqualTo("재구매 의사 없습니다.");
		}
	}

	@Nested
	@DisplayName("isWrittenBy()")
	class IsWrittenBy {

		@Test
		@DisplayName("작성자 id 와 일치하면 true 를 반환한다")
		void returnsTrueWhenSameMember() {
			// given
			Review review = ReviewFixture.create(product, member);

			// when & then
			assertThat(review.isWrittenBy(1L)).isTrue();
		}

		@Test
		@DisplayName("작성자 id 와 다르면 false 를 반환한다")
		void returnsFalseWhenDifferentMember() {
			// given
			Review review = ReviewFixture.create(product, member);

			// when & then
			assertThat(review.isWrittenBy(2L)).isFalse();
		}
	}
}
