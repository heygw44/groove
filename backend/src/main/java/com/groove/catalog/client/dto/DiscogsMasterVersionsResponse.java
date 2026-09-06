package com.groove.catalog.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code GET /masters/{id}/versions} 응답. */
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscogsMasterVersionsResponse(DiscogsSearchResponse.Pagination pagination, List<Version> versions) {

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Version(Long id, String label, String country, String title, List<String> majorFormats,
			String format, String catno, String released, String thumb) {
	}
}
