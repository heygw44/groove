package com.groove.catalog.dto;

public record CatalogLookupResponse(Long discogsReleaseId, String title, String artist, Integer year, String country,
		String catalogNo, String label, String thumbUrl, boolean alreadyImported) {
}
