package com.groove.catalog.dto;

import java.util.List;

import com.groove.product.entity.EditionType;

public record CatalogReleaseDetailResponse(Long discogsReleaseId, Long discogsMasterId, String title,
		String artistName, String labelName, String country, Integer pressingYear, String catalogNo, String barcode,
		EditionType editionType, List<String> genreNames, String imageUrl, String description) {
}
