package com.groove.product.dto;

import java.util.List;

public record ProductSuggestionResponse(
		List<Item> products,
		List<ArtistResponse> artists
) {

	public record Item(Long id, String title, String artistName, String thumbnailUrl) {
	}
}
