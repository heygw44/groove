package com.groove.catalog.dto;

import com.groove.global.common.PageLimits;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;

public record CatalogLookupRequest(
		String barcode,
		String catalogNo,
		String query,
		@PositiveOrZero @Max(PageLimits.MAX_PAGE) Integer page
) {

	public boolean hasAnyCriteria() {
		return isPresent(barcode) || isPresent(catalogNo) || isPresent(query);
	}

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
