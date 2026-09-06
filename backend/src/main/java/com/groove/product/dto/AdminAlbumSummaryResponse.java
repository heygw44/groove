package com.groove.product.dto;

import com.groove.product.entity.Album;

public record AdminAlbumSummaryResponse(
		Long id,
		String title,
		String artistName,
		Integer originalReleaseYear
) {

	public static AdminAlbumSummaryResponse from(Album album) {
		return new AdminAlbumSummaryResponse(album.getId(), album.getTitle(), album.getArtist().getName(),
				album.getOriginalReleaseYear());
	}
}
