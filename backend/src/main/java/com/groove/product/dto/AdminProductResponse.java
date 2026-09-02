package com.groove.product.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.groove.product.entity.Product;
import com.groove.product.entity.ProductGenre;
import com.groove.product.entity.ProductImage;
import com.groove.product.entity.ProductStatus;

public record AdminProductResponse(
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
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static AdminProductResponse from(Product product, int stockQuantity) {
		LabelSummary label = product.getLabel() == null
				? null
				: new LabelSummary(product.getLabel().getId(), product.getLabel().getName());
		List<GenreSummary> genres = product.getProductGenres().stream()
				.map(ProductGenre::getGenre)
				.map(genre -> new GenreSummary(genre.getId(), genre.getName()))
				.toList();
		List<ImageSummary> images = product.getImages().stream()
				.map(image -> new ImageSummary(image.getImageUrl(), image.getSortOrder()))
				.toList();

		return new AdminProductResponse(
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
				images,
				stockQuantity,
				product.getCreatedAt(),
				product.getUpdatedAt());
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
