package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.groove.limited.entity.LimitedDropStatus;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductGenre;
import com.groove.product.entity.ProductImage;
import com.groove.product.entity.ProductStatus;

public record ProductDetailResponse(
		Long id,
		String title,
		ArtistSummary artist,
		LabelSummary label,
		List<GenreSummary> genres,
		LocalDate releaseDate,
		String pressingInfo,
		String colorVariant,
		BigDecimal price,
		ProductStatus status,
		String description,
		List<ImageSummary> images,
		int stockQuantity,
		Double averageRating,
		long reviewCount,
		Boolean wishlisted,
		LimitedDropSummary limitedDrop
) {

	public static ProductDetailResponse from(Product product, List<ProductImage> images, int stockQuantity,
		Boolean wishlisted, LimitedDropSummary limitedDrop) {
		LabelSummary label = product.getLabel() == null
				? null
				: new LabelSummary(product.getLabel().getId(), product.getLabel().getName());
		List<GenreSummary> genres = product.getProductGenres().stream()
				.map(ProductGenre::getGenre)
				.map(genre -> new GenreSummary(genre.getId(), genre.getName()))
				.toList();
		List<ImageSummary> imageSummaries = images.stream()
				.map(image -> new ImageSummary(image.getImageUrl(), image.getSortOrder()))
				.toList();

		return new ProductDetailResponse(
				product.getId(),
				product.getTitle(),
				new ArtistSummary(product.getArtist().getId(), product.getArtist().getName()),
				label,
				genres,
				product.getReleaseDate(),
				product.getPressingInfo(),
				product.getColorVariant(),
				product.getPrice(),
				product.getStatus(),
				product.getDescription(),
				imageSummaries,
				stockQuantity,
				product.getAverageRating() == null ? null : product.getAverageRating().doubleValue(),
				product.getReviewCount(),
				wishlisted,
				limitedDrop);
	}

	public record ArtistSummary(Long id, String name) {
	}

	public record LabelSummary(Long id, String name) {
	}

	public record GenreSummary(Long id, String name) {
	}

	public record ImageSummary(String url, int sortOrder) {
	}

	public record LimitedDropSummary(
			Long id,
			LimitedDropStatus status,
			OffsetDateTime openAt,
			OffsetDateTime closeAt,
			int remainingQuantity,
			int perMemberLimit
	) {
	}
}
