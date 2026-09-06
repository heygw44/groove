package com.groove.recommend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.entity.Decade;

/** 점수 계산용 상품 특성. */
public record ProductFeature(Long id, Long artistId, Long labelId, Set<Long> genreIds, Decade decade,
		Double averageRating, LocalDateTime createdAt, boolean hidden) {

	public static ProductFeature from(ProductFeatureRow row) {
		return new ProductFeature(row.productId(), row.artistId(), row.labelId(), parseGenreIds(row.genreIds()),
				Decade.fromYear(row.releaseYear()), row.averageRating(), row.createdAt(),
				row.status() == ProductStatus.HIDDEN);
	}

	private static Set<Long> parseGenreIds(String genreIds) {
		if (genreIds == null || genreIds.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(genreIds.split(","))
				.map(Long::valueOf)
				.collect(Collectors.toSet());
	}
}
