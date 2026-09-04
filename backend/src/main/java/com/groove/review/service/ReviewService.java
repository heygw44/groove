package com.groove.review.service;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.member.entity.Member;
import com.groove.member.repository.MemberRepository;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderItemRepository;
import com.groove.product.entity.Product;
import com.groove.product.repository.ProductRepository;
import com.groove.review.dto.ReviewCreateRequest;
import com.groove.review.dto.ReviewEligibilityResponse;
import com.groove.review.dto.ReviewIneligibleReason;
import com.groove.review.dto.ReviewListRequest;
import com.groove.review.dto.ReviewResponse;
import com.groove.review.dto.ReviewStatsResponse;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.review.entity.Review;
import com.groove.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

/** 상품 리뷰 작성/조회/수정/삭제. 구매 확정(DELIVERED) 회원만 상품당 1회 작성할 수 있다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ProductRepository productRepository;
	private final MemberRepository memberRepository;
	private final OrderItemRepository orderItemRepository;

	@Transactional
	public ReviewResponse create(Long productId, Long memberId, ReviewCreateRequest request) {
		Member member = findActiveMember(memberId);
		Product product = findProduct(productId);
		if (!hasPurchased(productId, memberId)) {
			throw new BusinessException(ErrorCode.REVIEW_PURCHASE_REQUIRED);
		}
		if (reviewRepository.existsByProductIdAndMemberId(productId, memberId)) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
		}

		Review review = Review.create(product, member, request.rating(), request.title(), request.content());
		Review saved;
		try {
			saved = reviewRepository.saveAndFlush(review);
		} catch (DataIntegrityViolationException | CannotAcquireLockException e) {
			// 선검사만으로는 동시 작성 레이스를 못 막아 유니크 제약 위반을 여기서 한 번 더 잡는다.
			// InnoDB 는 같은 유니크 키로 INSERT 가 몰리면 중복키 대신 데드락을 내므로 락 획득 실패도 같이 잡는다.
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
		}

		ReviewResponse response = ReviewResponse.from(saved, memberId);
		productRepository.refreshReviewStats(productId);
		return response;
	}

	public PageResponse<ReviewResponse> getReviews(Long productId, Long memberId, ReviewListRequest request) {
		if (!productRepository.existsById(productId)) {
			throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
		}
		Page<Review> page = reviewRepository.findByProductId(productId, request.toPageable());
		return PageResponse.from(page.map(review -> ReviewResponse.from(review, memberId)));
	}

	public ReviewStatsResponse getStats(Long productId) {
		Product product = findProduct(productId);
		return ReviewStatsResponse.of(product, reviewRepository.countByRatingForProduct(productId));
	}

	public ReviewEligibilityResponse checkEligibility(Long productId, Long memberId) {
		findProduct(productId);
		if (memberId == null) {
			return ReviewEligibilityResponse.deny(ReviewIneligibleReason.LOGIN_REQUIRED);
		}
		if (!hasPurchased(productId, memberId)) {
			return ReviewEligibilityResponse.deny(ReviewIneligibleReason.PURCHASE_REQUIRED);
		}
		if (reviewRepository.existsByProductIdAndMemberId(productId, memberId)) {
			return ReviewEligibilityResponse.deny(ReviewIneligibleReason.ALREADY_REVIEWED);
		}
		return ReviewEligibilityResponse.allow();
	}

	@Transactional
	public ReviewResponse update(Long reviewId, Long memberId, ReviewUpdateRequest request) {
		Review review = findOwnedReview(reviewId, memberId);
		review.update(request.rating(), request.title(), request.content());
		Long productId = review.getProduct().getId();
		ReviewResponse response = ReviewResponse.from(review, memberId);
		productRepository.refreshReviewStats(productId);
		return response;
	}

	@Transactional
	public void delete(Long reviewId, Long memberId) {
		Review review = findOwnedReview(reviewId, memberId);
		Long productId = review.getProduct().getId();
		reviewRepository.delete(review);
		productRepository.refreshReviewStats(productId);
	}

	private Review findOwnedReview(Long reviewId, Long memberId) {
		return reviewRepository.findByIdAndMemberId(reviewId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
	}

	private boolean hasPurchased(Long productId, Long memberId) {
		return orderItemRepository.existsByOrderMemberIdAndProductIdAndOrderStatus(memberId, productId,
				OrderStatus.DELIVERED);
	}

	private Product findProduct(Long productId) {
		return productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
	}

	private Member findActiveMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isWithdrawn()) {
			throw new BusinessException(ErrorCode.MEMBER_WITHDRAWN);
		}
		return member;
	}
}
