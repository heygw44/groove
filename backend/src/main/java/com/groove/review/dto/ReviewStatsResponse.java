package com.groove.review.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.groove.product.entity.Product;

/** 상품 상세의 별점 요약(평균·개수·1~5점 분포). */
public record ReviewStatsResponse(BigDecimal averageRating, int reviewCount, Map<Integer, Long> distribution) {

	private static final int MIN_RATING = 1;
	private static final int MAX_RATING = 5;

	public static ReviewStatsResponse of(Product product, List<ReviewRatingCount> counts) {
		Map<Integer, Long> distribution = new TreeMap<>();
		for (int rating = MIN_RATING; rating <= MAX_RATING; rating++) {
			distribution.put(rating, 0L);
		}
		counts.forEach(count -> distribution.put(count.rating(), count.count()));
		return new ReviewStatsResponse(product.getAverageRating(), product.getReviewCount(), distribution);
	}
}
