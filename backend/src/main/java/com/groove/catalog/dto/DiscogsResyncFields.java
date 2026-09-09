package com.groove.catalog.dto;

import com.groove.product.entity.EditionType;

/** Discogs 재검증으로 갱신 가능한 다섯 필드. {@code Product.applyDiscogsSync} 시그니처와 짝을 맞춘다. */
public record DiscogsResyncFields(
	String country,
	Integer pressingYear,
	String catalogNo,
	String barcode,
	EditionType editionType
) {
}
