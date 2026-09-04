package com.groove.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.MemberFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.ReviewFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.review.dto.ReviewEligibilityResponse;
import com.groove.review.dto.ReviewIneligibleReason;
import com.groove.review.dto.ReviewListRequest;
import com.groove.review.dto.ReviewRatingCount;
import com.groove.review.dto.ReviewResponse;
import com.groove.review.dto.ReviewStatsResponse;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

	private static final Long MEMBER_ID = 1L;
	private static final Long PRODUCT_ID = 10L;
	private static final Long REVIEW_ID = 100L;

	@Mock
	ReviewRepository reviewRepository;

	@Mock
	ProductRepository productRepository;

	@Mock
	MemberRepository memberRepository;

	@Mock
	OrderItemRepository orderItemRepository;

	ReviewService reviewService;

	Member member;
	Product product;

	@BeforeEach
	void setUp() {
		reviewService = new ReviewService(reviewRepository, productRepository, memberRepository,
				orderItemRepository);
		member = MemberFixture.withId(MemberFixture.create(), MEMBER_ID);
		Artist artist = ArtistFixture.create();
		product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("구매 확정 회원이 최초로 작성하면 리뷰를 생성한다")
		void createsReviewForFirstTimePurchaser() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(true);
			given(reviewRepository.existsByProductIdAndMemberId(PRODUCT_ID, MEMBER_ID)).willReturn(false);
			Review saved = ReviewFixture.withId(ReviewFixture.create(product, member), REVIEW_ID);
			given(reviewRepository.saveAndFlush(any())).willReturn(saved);

			// when
			ReviewResponse response = reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest());

			// then
			assertThat(response.id()).isEqualTo(REVIEW_ID);
			assertThat(response.mine()).isTrue();
			verify(productRepository).refreshReviewStats(PRODUCT_ID);
		}

		@Test
		@DisplayName("구매하지 않은 회원이면 REVIEW_PURCHASE_REQUIRED 예외를 던진다")
		void throwsWhenNotPurchased() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(false);

			// when & then
			assertThatThrownBy(
					() -> reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_PURCHASE_REQUIRED);
			verify(reviewRepository, never()).saveAndFlush(any());
			verify(productRepository, never()).refreshReviewStats(any());
		}

		@Test
		@DisplayName("이미 작성한 리뷰가 있으면 REVIEW_ALREADY_EXISTS 예외를 던진다")
		void throwsWhenAlreadyExists() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(true);
			given(reviewRepository.existsByProductIdAndMemberId(PRODUCT_ID, MEMBER_ID)).willReturn(true);

			// when & then
			assertThatThrownBy(
					() -> reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
			verify(reviewRepository, never()).saveAndFlush(any());
			verify(productRepository, never()).refreshReviewStats(any());
		}

		@Test
		@DisplayName("유니크 제약 위반이 발생하면 REVIEW_ALREADY_EXISTS 예외로 변환한다")
		void translatesDataIntegrityViolationToAlreadyExists() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(true);
			given(reviewRepository.existsByProductIdAndMemberId(PRODUCT_ID, MEMBER_ID)).willReturn(false);
			given(reviewRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

			// when & then
			assertThatThrownBy(
					() -> reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
			verify(productRepository, never()).refreshReviewStats(any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 MEMBER_WITHDRAWN 예외를 던진다")
		void throwsWhenMemberWithdrawn() {
			// given
			Member withdrawn = MemberFixture.withId(MemberFixture.createWithdrawn(), MEMBER_ID);
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(withdrawn));

			// when & then
			assertThatThrownBy(
					() -> reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.MEMBER_WITHDRAWN);
			verify(reviewRepository, never()).saveAndFlush(any());
			verify(productRepository, never()).refreshReviewStats(any());
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(
					() -> reviewService.create(PRODUCT_ID, MEMBER_ID, ReviewFixture.createRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
			verify(productRepository, never()).refreshReviewStats(any());
		}
	}

	@Nested
	@DisplayName("getReviews()")
	class GetReviews {

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.existsById(PRODUCT_ID)).willReturn(false);

			// when & then
			assertThatThrownBy(() -> reviewService.getReviews(PRODUCT_ID, MEMBER_ID,
					new ReviewListRequest(null, null, null)))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("정렬 조건을 Pageable 로 변환해 조회하고 mine 플래그를 채운다")
		void convertsSortToPageableAndFillsMineFlag() {
			// given
			given(productRepository.existsById(PRODUCT_ID)).willReturn(true);
			Review mine = ReviewFixture.withId(ReviewFixture.create(product, member), REVIEW_ID);
			Member other = MemberFixture.withId(MemberFixture.create("other@groove.com"), 2L);
			Review others = ReviewFixture.withId(ReviewFixture.create(product, other), 101L);
			given(reviewRepository.findByProductId(eq(PRODUCT_ID), any(Pageable.class)))
					.willReturn(new PageImpl<>(List.of(mine, others)));

			// when
			PageResponse<ReviewResponse> response = reviewService.getReviews(PRODUCT_ID, MEMBER_ID,
					new ReviewListRequest("ratingDesc", 0, 10));

			// then
			ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
			verify(reviewRepository).findByProductId(eq(PRODUCT_ID), captor.capture());
			Pageable used = captor.getValue();
			assertThat(used).isEqualTo(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "rating")
					.and(Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
			assertThat(response.content()).extracting(ReviewResponse::mine).containsExactly(true, false);
		}
	}

	@Nested
	@DisplayName("getStats()")
	class GetStats {

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> reviewService.getStats(PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("작성된 별점이 없는 구간은 0 으로 채운다")
		void fillsMissingRatingsWithZero() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(reviewRepository.countByRatingForProduct(PRODUCT_ID))
					.willReturn(List.of(new ReviewRatingCount(5, 2L), new ReviewRatingCount(3, 1L)));

			// when
			ReviewStatsResponse response = reviewService.getStats(PRODUCT_ID);

			// then
			assertThat(response.distribution()).containsExactly(entry(1, 0L), entry(2, 0L), entry(3, 1L),
					entry(4, 0L), entry(5, 2L));
		}
	}

	@Nested
	@DisplayName("checkEligibility()")
	class CheckEligibility {

		@Test
		@DisplayName("비로그인이면 LOGIN_REQUIRED 를 반환한다")
		void returnsLoginRequiredWhenAnonymous() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when
			ReviewEligibilityResponse response = reviewService.checkEligibility(PRODUCT_ID, null);

			// then
			assertThat(response.eligible()).isFalse();
			assertThat(response.reason()).isEqualTo(ReviewIneligibleReason.LOGIN_REQUIRED);
		}

		@Test
		@DisplayName("구매하지 않았으면 PURCHASE_REQUIRED 를 반환한다")
		void returnsPurchaseRequiredWhenNotPurchased() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(false);

			// when
			ReviewEligibilityResponse response = reviewService.checkEligibility(PRODUCT_ID, MEMBER_ID);

			// then
			assertThat(response.eligible()).isFalse();
			assertThat(response.reason()).isEqualTo(ReviewIneligibleReason.PURCHASE_REQUIRED);
		}

		@Test
		@DisplayName("이미 작성했으면 ALREADY_REVIEWED 를 반환한다")
		void returnsAlreadyReviewedWhenExists() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(true);
			given(reviewRepository.existsByProductIdAndMemberId(PRODUCT_ID, MEMBER_ID)).willReturn(true);

			// when
			ReviewEligibilityResponse response = reviewService.checkEligibility(PRODUCT_ID, MEMBER_ID);

			// then
			assertThat(response.eligible()).isFalse();
			assertThat(response.reason()).isEqualTo(ReviewIneligibleReason.ALREADY_REVIEWED);
		}

		@Test
		@DisplayName("구매했고 작성 이력이 없으면 작성 가능하다")
		void returnsEligibleWhenPurchasedAndNotReviewed() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(MEMBER_ID, PRODUCT_ID,
					OrderStatus.DELIVERED)).willReturn(true);
			given(reviewRepository.existsByProductIdAndMemberId(PRODUCT_ID, MEMBER_ID)).willReturn(false);

			// when
			ReviewEligibilityResponse response = reviewService.checkEligibility(PRODUCT_ID, MEMBER_ID);

			// then
			assertThat(response.eligible()).isTrue();
			assertThat(response.reason()).isNull();
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("작성자 본인이면 내용을 수정한다")
		void updatesReviewWhenOwner() {
			// given
			Review review = ReviewFixture.withId(ReviewFixture.create(product, member), REVIEW_ID);
			given(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID)).willReturn(Optional.of(review));
			ReviewUpdateRequest request = ReviewFixture.updateRequest();

			// when
			ReviewResponse response = reviewService.update(REVIEW_ID, MEMBER_ID, request);

			// then
			assertThat(response.rating()).isEqualTo(request.rating());
			assertThat(response.title()).isEqualTo(request.title());
			verify(productRepository).refreshReviewStats(PRODUCT_ID);
		}

		@Test
		@DisplayName("타인의 리뷰면 REVIEW_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			given(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(
					() -> reviewService.update(REVIEW_ID, MEMBER_ID, ReviewFixture.updateRequest()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
			verify(productRepository, never()).refreshReviewStats(any());
		}
	}

	@Nested
	@DisplayName("delete()")
	class Delete {

		@Test
		@DisplayName("작성자 본인이면 리뷰를 삭제한다")
		void deletesReviewWhenOwner() {
			// given
			Review review = ReviewFixture.withId(ReviewFixture.create(product, member), REVIEW_ID);
			given(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID)).willReturn(Optional.of(review));

			// when
			reviewService.delete(REVIEW_ID, MEMBER_ID);

			// then
			verify(reviewRepository).delete(review);
			verify(productRepository).refreshReviewStats(PRODUCT_ID);
		}

		@Test
		@DisplayName("타인의 리뷰면 REVIEW_NOT_FOUND 예외를 던진다")
		void throwsWhenNotOwner() {
			// given
			given(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> reviewService.delete(REVIEW_ID, MEMBER_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
			verify(reviewRepository, never()).delete(any(Review.class));
			verify(productRepository, never()).refreshReviewStats(any());
		}
	}
}
