package com.groove.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.review.dto.ReviewSortType;
import com.groove.review.entity.Review;
import com.groove.support.DataJpaTestSupport;

class ReviewRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 상품에 같은 회원이 두 번 작성하면 유니크 제약 위반이 발생한다")
		void throwsWhenProductAndMemberDuplicated() {
			// given
			Member member = memberRepository.save(MemberFixture.create("review-repo-uk@groove.com"));
			Product product = productRepository.save(ProductFixture.create(artistRepository.save(
					ArtistFixture.create("review-repo-uk"))));
			reviewRepository.saveAndFlush(ReviewFixture.create(product, member));

			// when & then
			assertThatThrownBy(() -> reviewRepository.saveAndFlush(ReviewFixture.create(product, member)))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Nested
	@DisplayName("findByProductId()")
	class FindByProductId {

		@Test
		@DisplayName("ratingDesc 정렬이면 평점 내림차순으로 반환한다")
		void sortsByRatingDescending() {
			// given
			Member first = memberRepository.save(MemberFixture.create("review-repo-rating-1@groove.com"));
			Member second = memberRepository.save(MemberFixture.create("review-repo-rating-2@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("review-repo-rating"));
			Product product = productRepository.save(ProductFixture.create(artist));
			Review low = reviewRepository.save(ReviewFixture.create(product, first, 2));
			Review high = reviewRepository.save(ReviewFixture.create(product, second, 5));

			// when
			List<Review> result = reviewRepository.findByProductId(product.getId(),
					PageRequest.of(0, 10, ReviewSortType.RATING_DESC.toSort())).getContent();

			// then
			assertThat(result).extracting(Review::getId).containsExactly(high.getId(), low.getId());
		}

		@Test
		@DisplayName("latest 정렬이면 최신 작성순으로 반환한다")
		void sortsByLatest() {
			// given
			Member first = memberRepository.save(MemberFixture.create("review-repo-latest-1@groove.com"));
			Member second = memberRepository.save(MemberFixture.create("review-repo-latest-2@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("review-repo-latest"));
			Product product = productRepository.save(ProductFixture.create(artist));
			Review earlier = reviewRepository.saveAndFlush(ReviewFixture.create(product, first));
			Review later = reviewRepository.saveAndFlush(ReviewFixture.create(product, second));

			// when
			List<Review> result = reviewRepository.findByProductId(product.getId(),
					PageRequest.of(0, 10, ReviewSortType.LATEST.toSort())).getContent();

			// then
			assertThat(result).extracting(Review::getId).containsExactly(later.getId(), earlier.getId());
		}
	}

	@Nested
	@DisplayName("findByIdAndMemberId()")
	class FindByIdAndMemberId {

		@Test
		@DisplayName("타인의 리뷰면 빈 Optional 을 반환한다")
		void returnsEmptyForOtherMember() {
			// given
			Member owner = memberRepository.save(MemberFixture.create("review-repo-owner@groove.com"));
			Member other = memberRepository.save(MemberFixture.create("review-repo-other@groove.com"));
			Artist artist = artistRepository.save(ArtistFixture.create("review-repo-owner"));
			Product product = productRepository.save(ProductFixture.create(artist));
			Review review = reviewRepository.save(ReviewFixture.create(product, owner));

			// when
			Optional<Review> result = reviewRepository.findByIdAndMemberId(review.getId(), other.getId());

			// then
			assertThat(result).isEmpty();
		}
	}
}
