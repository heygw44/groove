package com.groove.product.dto;

import java.util.List;

import com.groove.product.entity.Album;

public record AlbumDetailResponse(
		Long id,
		String title,
		ProductDetailResponse.ArtistSummary artist,
		Integer originalReleaseYear,
		String description,
		List<ProductSummaryResponse> pressings,
		Boolean watched
) {

	public static AlbumDetailResponse from(Album album, List<ProductSummaryResponse> pressings, Boolean watched) {
		return new AlbumDetailResponse(
				album.getId(),
				album.getTitle(),
				new ProductDetailResponse.ArtistSummary(album.getArtist().getId(), album.getArtist().getName()),
				album.getOriginalReleaseYear(),
				album.getDescription(),
				pressings,
				watched);
	}
}
