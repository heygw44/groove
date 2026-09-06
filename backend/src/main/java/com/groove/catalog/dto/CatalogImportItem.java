package com.groove.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import com.groove.product.entity.EditionType;

/**
 * Discogs 릴리즈를 상품으로 등록하기 위해 매핑한 값. 단건 등록과 배치 Writer 가 공유한다.
 */
public record CatalogImportItem(
	Long discogsReleaseId,
	Long discogsMasterId,
	String title,
	String artistName,
	String labelName,
	String country,
	Integer pressingYear,
	String catalogNo,
	String barcode,
	EditionType editionType,
	List<String> genreNames,
	BigDecimal price
) {
}
