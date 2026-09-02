package com.groove.product.dto;

import com.groove.product.entity.Artist;

public record ArtistResponse(Long id, String name, String nameEn) {

	public static ArtistResponse from(Artist artist) {
		return new ArtistResponse(artist.getId(), artist.getName(), artist.getNameEn());
	}
}
