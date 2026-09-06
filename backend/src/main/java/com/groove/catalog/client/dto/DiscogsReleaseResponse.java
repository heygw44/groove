package com.groove.catalog.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code GET /releases/{id}} 응답. */
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscogsReleaseResponse(Long id, String title, List<Artist> artists, List<Label> labels,
		String country, Integer year, List<String> genres, List<String> styles, List<Format> formats,
		List<Identifier> identifiers, List<Image> images, Long masterId, String notes) {

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Artist(String name) {
	}

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Label(String name, String catno) {
	}

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Format(String name, List<String> descriptions) {
	}

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Identifier(String type, String value) {
	}

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Image(String type, String uri, String uri150) {
	}
}
