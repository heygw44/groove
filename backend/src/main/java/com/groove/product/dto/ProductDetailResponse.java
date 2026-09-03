package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
		Boolean wishlisted
) {

	public static ProductDetailResponse from(Product product, List<ProductImage> images, int stockQuantity,
		Boolean wishlisted) {
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
				// 리뷰 미구현이라 고정값
				null,
				0L,
				wishlisted);
	}

	public record ArtistSummary(Long id, String name) {
	}

	public record LabelSummary(Long id, String name) {
	}

	public record GenreSummary(Long id, String name) {
	}

	public record ImageSummary(String url, int sortOrder) {
	}
}
