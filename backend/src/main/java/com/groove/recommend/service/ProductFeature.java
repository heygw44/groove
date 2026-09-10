package com.groove.recommend.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.groove.product.entity.ProductStatus;
import com.groove.recommend.dto.ProductFeatureRow;
import com.groove.recommend.entity.Decade;

/** 점수 계산용 상품 특성. */
public record ProductFeature(Long id, Long albumId, Long artistId, Long labelId, Set<Long> genreIds, Decade decade,
		Double averageRating, LocalDateTime createdAt, int reviewCount, long soldQuantity, ProductStatus status) {

	public static ProductFeature from(ProductFeatureRow row) {
		return new ProductFeature(row.productId(), row.albumId(), row.artistId(), row.labelId(),
				parseGenreIds(row.genreIds()), Decade.fromYear(row.releaseYear()), row.averageRating(),
				row.createdAt(), nullToZero(row.reviewCount()), nullToZero(row.soldQuantity()), row.status());
	}

	/** HIDDEN 여부는 별도 필드가 아니라 status 에서 파생한다. */
	public boolean hidden() {
		return status == ProductStatus.HIDDEN;
	}

	private static int nullToZero(Integer value) {
		return value == null ? 0 : value;
	}

	private static long nullToZero(Long value) {
		return value == null ? 0L : value;
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
