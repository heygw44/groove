package com.groove.catalog.client.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** {@code GET /database/search} 응답. */
@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscogsSearchResponse(Pagination pagination, List<Result> results) {

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(Long id, String type, String title, String year, String country, String catno,
			List<String> label, List<String> format, String thumb, Long masterId) {
	}

	@JsonNaming(SnakeCaseStrategy.class)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Pagination(int page, int pages, int perPage, long items) {
	}
}
