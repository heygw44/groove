package com.groove.recommend.dto;

import java.time.LocalDateTime;

import com.groove.product.entity.ProductStatus;

/** 점수 계산에 필요한 상품 특성만 담은 경량 행. */
public record ProductFeatureRow(Long productId, Long artistId, Long labelId, Integer releaseYear,
		Double averageRating, LocalDateTime createdAt, ProductStatus status, String genreIds) {
}
