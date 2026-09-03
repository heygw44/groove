package com.groove.review.dto;

import java.time.LocalDateTime;

import com.groove.review.entity.Review;

public record ReviewResponse(
		Long id,
		Long productId,
		String nickname,
		int rating,
		String title,
		String content,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		boolean mine
) {

	public static ReviewResponse from(Review review, Long memberId) {
		return new ReviewResponse(
				review.getId(),
				review.getProduct().getId(),
				review.getMember().getNickname(),
				review.getRating(),
				review.getTitle(),
				review.getContent(),
				review.getCreatedAt(),
				review.getUpdatedAt(),
				memberId != null && review.isWrittenBy(memberId)
		);
	}
}
