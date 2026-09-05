package com.groove.product.dto;

import com.groove.product.entity.Genre;

public record GenreResponse(Long id, String name) {

	public static GenreResponse from(Genre genre) {
		return new GenreResponse(genre.getId(), genre.getName());
	}
}
