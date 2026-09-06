package com.groove.catalog.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.dto.CatalogImportItem;
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
	private static final String VINYL_FORMAT_NAME = "Vinyl";

	// Discogs 표기가 우리 장르명과 문자열 자체가 달라 정규화로도 못 맞추는 것만 명시적으로 잇는다.
	private static final Map<String, String> GENRE_ALIASES = Map.of(
			normalizeGenre("Stage & Screen"), "OST",
			normalizeGenre("Funk / Soul"), "R&B");

	public CatalogLookupResponse toLookup(DiscogsSearchResponse.Result result, boolean alreadyImported) {
		String[] artistAndTitle = splitArtistAndTitle(result.title());
		return new CatalogLookupResponse(result.id(), artistAndTitle[1], artistAndTitle[0], parseYear(result.year()),
				result.country(), result.catno(), firstOrNull(result.label()), blankToNull(result.thumb()),
				alreadyImported);
	}

	public CatalogReleaseDetailResponse toDetail(DiscogsReleaseResponse release, Collection<String> knownGenreNames) {
		String imageUrl = resolveImageUrl(release.images());

		return new CatalogReleaseDetailResponse(release.id(), release.masterId(), release.title(),
				firstArtistName(release.artists()), firstLabelName(release.labels()), release.country(),
				resolvePressingYear(release), firstCatalogNo(release.labels()), firstBarcode(release.identifiers()),
				resolveEditionType(release.formats()),
				matchGenreNames(release.genres(), release.styles(), knownGenreNames), imageUrl,
				blankToNull(release.notes()));
	}

	public CatalogImportItem toImportItem(DiscogsReleaseResponse release, Collection<String> knownGenreNames,
			Long masterIdOverride, BigDecimal price) {
		Long discogsMasterId = masterIdOverride != null ? masterIdOverride : release.masterId();

		return new CatalogImportItem(release.id(), discogsMasterId, release.title(),
				firstArtistName(release.artists()), firstLabelName(release.labels()), release.country(),
				resolvePressingYear(release), firstCatalogNo(release.labels()), firstBarcode(release.identifiers()),
				resolveEditionType(release.formats()),
				matchGenreNames(release.genres(), release.styles(), knownGenreNames), price);
	}

	public boolean isVinyl(DiscogsReleaseResponse release) {
		if (release.formats() == null) {
			return false;
		}
		return release.formats().stream()
				.map(DiscogsReleaseResponse.Format::name)
				.anyMatch(name -> VINYL_FORMAT_NAME.equalsIgnoreCase(name));
	}

	public boolean isVinylFormat(String versionFormat) {
		return versionFormat != null && versionFormat.toLowerCase().contains(VINYL_FORMAT_NAME.toLowerCase());
	}

	/**
	 * {@code GET /masters/{id}/versions} 의 {@code format} 필드에는 "LP, Album" 처럼 오고 "Vinyl" 이라는
	 * 단어가 들어오지 않는다. 포맷 구분은 {@code major_formats} 로 판정하고, 값이 없을 때만 문자열 판정으로 폴백한다.
	 */
	public boolean isVinylVersion(DiscogsMasterVersionsResponse.Version version) {
		List<String> majorFormats = version.majorFormats();
		if (majorFormats == null || majorFormats.isEmpty()) {
			return isVinylFormat(version.format());
		}
		return majorFormats.stream().anyMatch(VINYL_FORMAT_NAME::equalsIgnoreCase);
	}

	private Integer resolvePressingYear(DiscogsReleaseResponse release) {
		return release.year() == null || release.year() <= 0 ? null : release.year();
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
			findKnownGenre(candidate, knownGenreNames).ifPresent(matched::add);
		}
		return List.copyOf(matched);
	}

	private Optional<String> findKnownGenre(String candidate, Collection<String> knownGenreNames) {
		String normalizedCandidate = normalizeGenre(candidate);
		String normalizedAliasTarget = normalizeGenre(GENRE_ALIASES.get(normalizedCandidate));
		for (String known : knownGenreNames) {
			String normalizedKnown = normalizeGenre(known);
			if (normalizedKnown.equals(normalizedCandidate) || normalizedKnown.equals(normalizedAliasTarget)) {
				return Optional.of(known);
			}
		}
		return Optional.empty();
	}

	/** 소문자화 후 공백·하이픈·`&`를 제거해 "Hip Hop" 과 "Hip-Hop" 처럼 표기만 다른 이름을 같게 만든다. */
	private static String normalizeGenre(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-&]", "");
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
