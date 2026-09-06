package com.groove.catalog.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.product.entity.EditionType;

/** Discogs 원본 응답을 카탈로그 DTO 로 옮기는 순수 변환 로직. */
@Component
public class DiscogsReleaseMapper {

	private static final String TITLE_SEPARATOR = " - ";
	private static final String NONE_CATALOG_NO = "none";
	private static final String BARCODE_TYPE = "Barcode";
	private static final String IMAGE_TYPE_PRIMARY = "primary";
	private static final String DESC_LIMITED_EDITION = "limited edition";
	private static final String DESC_PROMO = "promo";
	private static final String DESC_REMASTERED = "remastered";
	private static final String DESC_REISSUE = "reissue";
	private static final String DESC_REPRESS = "repress";

	public CatalogLookupResponse toLookup(DiscogsSearchResponse.Result result, boolean alreadyImported) {
		String[] artistAndTitle = splitArtistAndTitle(result.title());
		return new CatalogLookupResponse(result.id(), artistAndTitle[1], artistAndTitle[0], parseYear(result.year()),
				result.country(), result.catno(), firstOrNull(result.label()), blankToNull(result.thumb()),
				alreadyImported);
	}

	public CatalogReleaseDetailResponse toDetail(DiscogsReleaseResponse release, Collection<String> knownGenreNames) {
		String artistName = firstArtistName(release.artists());
		String labelName = firstLabelName(release.labels());
		String catalogNo = firstCatalogNo(release.labels());
		String barcode = firstBarcode(release.identifiers());
		EditionType editionType = resolveEditionType(release.formats());
		List<String> genreNames = matchGenreNames(release.genres(), release.styles(), knownGenreNames);
		String imageUrl = resolveImageUrl(release.images());
		Integer pressingYear = release.year() == null || release.year() <= 0 ? null : release.year();

		return new CatalogReleaseDetailResponse(release.id(), release.masterId(), release.title(), artistName,
				labelName, release.country(), pressingYear, catalogNo, barcode, editionType, genreNames, imageUrl,
				blankToNull(release.notes()));
	}

	private String[] splitArtistAndTitle(String title) {
		if (title == null) {
			return new String[] {null, null};
		}
		int index = title.indexOf(TITLE_SEPARATOR);
		if (index < 0) {
			return new String[] {null, title};
		}
		return new String[] {title.substring(0, index), title.substring(index + TITLE_SEPARATOR.length())};
	}

	private Integer parseYear(String year) {
		if (year == null || year.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(year.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private String firstOrNull(List<String> values) {
		return values == null || values.isEmpty() ? null : values.get(0);
	}

	private String firstArtistName(List<DiscogsReleaseResponse.Artist> artists) {
		if (artists == null || artists.isEmpty()) {
			return null;
		}
		String name = artists.get(0).name();
		if (name == null) {
			return null;
		}
		return name.replaceAll("\\s\\(\\d+\\)$", "");
	}

	private String firstLabelName(List<DiscogsReleaseResponse.Label> labels) {
		if (labels == null || labels.isEmpty()) {
			return null;
		}
		return labels.get(0).name();
	}

	private String firstCatalogNo(List<DiscogsReleaseResponse.Label> labels) {
		if (labels == null || labels.isEmpty()) {
			return null;
		}
		String catalogNo = labels.get(0).catno();
		if (catalogNo == null || catalogNo.isBlank() || catalogNo.equalsIgnoreCase(NONE_CATALOG_NO)) {
			return null;
		}
		return catalogNo;
	}

	private String firstBarcode(List<DiscogsReleaseResponse.Identifier> identifiers) {
		if (identifiers == null) {
			return null;
		}
		return identifiers.stream()
				.filter(identifier -> BARCODE_TYPE.equalsIgnoreCase(identifier.type()))
				.map(DiscogsReleaseResponse.Identifier::value)
				.filter(value -> value != null)
				.map(value -> value.replaceAll("[\\s-]", ""))
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse(null);
	}

	private EditionType resolveEditionType(List<DiscogsReleaseResponse.Format> formats) {
		if (formats == null) {
			return EditionType.STANDARD;
		}
		Set<String> descriptions = new LinkedHashSet<>();
		for (DiscogsReleaseResponse.Format format : formats) {
			if (format.descriptions() != null) {
				format.descriptions().stream()
						.filter(Objects::nonNull)
						.map(String::toLowerCase)
						.forEach(descriptions::add);
			}
		}
		if (descriptions.contains(DESC_LIMITED_EDITION)) {
			return EditionType.LIMITED;
		}
		if (descriptions.contains(DESC_PROMO)) {
			return EditionType.PROMO;
		}
		if (descriptions.contains(DESC_REMASTERED)) {
			return EditionType.REMASTER;
		}
		if (descriptions.contains(DESC_REISSUE) || descriptions.contains(DESC_REPRESS)) {
			return EditionType.REISSUE;
		}
		return EditionType.STANDARD;
	}

	private List<String> matchGenreNames(List<String> genres, List<String> styles, Collection<String> knownGenreNames) {
		List<String> candidates = new ArrayList<>();
		if (genres != null) {
			candidates.addAll(genres);
		}
		if (styles != null) {
			candidates.addAll(styles);
		}
		Set<String> matched = new LinkedHashSet<>();
		for (String candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			for (String known : knownGenreNames) {
				if (known.equalsIgnoreCase(candidate)) {
					matched.add(known);
					break;
				}
			}
		}
		return List.copyOf(matched);
	}

	private String resolveImageUrl(List<DiscogsReleaseResponse.Image> images) {
		if (images == null || images.isEmpty()) {
			return null;
		}
		return images.stream()
				.filter(image -> IMAGE_TYPE_PRIMARY.equalsIgnoreCase(image.type()))
				.map(DiscogsReleaseResponse.Image::uri)
				.findFirst()
				.orElse(images.get(0).uri());
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
