package com.groove.fixture;

import org.springframework.test.util.ReflectionTestUtils;

import com.groove.member.entity.Member;
import com.groove.product.entity.Product;
import com.groove.review.dto.ReviewCreateRequest;
import com.groove.review.dto.ReviewUpdateRequest;
import com.groove.review.entity.Review;

public final class ReviewFixture {

	private static final int RATING = 5;
	private static final String TITLE = "최고의 앨범";
	private static final String CONTENT = "믹싱이 훌륭합니다.";

	private ReviewFixture() {
	}

	public static Review create(Product product, Member member) {
		return Review.create(product, member, RATING, TITLE, CONTENT);
	}

	public static Review create(Product product, Member member, int rating) {
		return Review.create(product, member, rating, TITLE, CONTENT);
	}

	public static Review withId(Review review, Long id) {
		ReflectionTestUtils.setField(review, "id", id);
		return review;
	}

	public static ReviewCreateRequest createRequest() {
		return new ReviewCreateRequest(RATING, TITLE, CONTENT);
	}

	public static ReviewCreateRequest createRequest(int rating) {
		return new ReviewCreateRequest(rating, TITLE, CONTENT);
	}

	public static ReviewUpdateRequest updateRequest() {
		return new ReviewUpdateRequest(4, "다시 들어도 좋네요", "재구매 의사 있습니다.");
	}
}
