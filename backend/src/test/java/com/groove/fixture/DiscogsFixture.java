package com.groove.fixture;

import java.util.List;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;

/** Discogs 응답 고정값. JSON 은 실제 응답 형태를 그대로 옮긴다. */
public final class DiscogsFixture {

	public static final String SEARCH_RESPONSE_JSON = """
			{
				"pagination": { "page": 1, "pages": 8, "per_page": 20, "items": 150 },
				"results": [
					{
						"id": 7097051,
						"type": "release",
						"title": "Nirvana - Nevermind",
						"year": "2015",
						"country": "Europe",
						"catno": "424 425-1",
						"label": ["DGC", "Sub Pop"],
						"format": ["Vinyl", "LP"],
						"thumb": "https://i.discogs.com/thumb.jpeg",
						"master_id": 13814
					}
				]
			}
			""";

	public static final String RELEASE_RESPONSE_JSON = """
			{
				"id": 249504,
				"title": "Kind Of Blue",
				"artists": [{ "name": "Miles Davis" }],
				"labels": [{ "name": "Columbia", "catno": "CS 8163" }],
				"country": "Germany",
				"year": 1959,
				"genres": ["Jazz"],
				"styles": ["Cool Jazz"],
				"formats": [{ "name": "Vinyl", "descriptions": ["LP", "Album"] }],
				"identifiers": [{ "type": "Barcode", "value": "5012394144777" }],
				"images": [{ "type": "primary", "uri": "https://i.discogs.com/large.jpeg",
					"uri150": "https://i.discogs.com/150.jpeg" }],
				"master_id": 21247,
				"notes": "Discogs 원본 노트"
			}
			""";

	public static final String MASTER_VERSIONS_RESPONSE_JSON = """
			{
				"pagination": { "page": 1, "pages": 1, "per_page": 20, "items": 1 },
				"versions": [
					{ "id": 3193, "label": "Ninja Tune", "country": "UK", "title": "Bricolage",
						"major_formats": ["Vinyl"], "format": "LP, Album", "catno": "zen CD29", "released": "1997",
						"thumb": "https://i.discogs.com/version.jpeg" }
				]
			}
			""";

	public static final String NOT_FOUND_RESPONSE_JSON = """
			{ "message": "Release not found." }
			""";

	private DiscogsFixture() {
	}

	public static DiscogsSearchResponse.Result searchResult(long id, String title, String year) {
		return searchResult(id, title, year, List.of("Columbia"), "https://i.discogs.com/thumb.jpeg");
	}

	public static DiscogsSearchResponse.Result searchResult(long id, String title, String year, List<String> label,
			String thumb) {
		return new DiscogsSearchResponse.Result(id, "release", title, year, "Europe", "CS 8163", label,
				List.of("Vinyl", "LP"), thumb, 21247L);
	}

	public static DiscogsMasterVersionsResponse.Version version(long id, String format) {
		return version(id, List.of("Vinyl"), format);
	}

	public static DiscogsMasterVersionsResponse.Version version(long id, List<String> majorFormats, String format) {
		return new DiscogsMasterVersionsResponse.Version(id, "Columbia", "UK & Europe", "Test Release", majorFormats,
				format, "CS 8163", "1959", "https://i.discogs.com/version.jpeg");
	}

	public static DiscogsMasterVersionsResponse masterVersionsResponse(int page, int pages,
			List<DiscogsMasterVersionsResponse.Version> versions) {
		DiscogsSearchResponse.Pagination pagination = new DiscogsSearchResponse.Pagination(page + 1, pages, 20,
				versions.size());
		return new DiscogsMasterVersionsResponse(pagination, versions);
	}

	public static DiscogsReleaseResponse releaseResponse(String artistName, String labelName, String catalogNo,
			List<String> formatDescriptions, String barcode, List<String> genres, List<String> styles) {
		return releaseResponse(artistName, labelName, catalogNo, formatDescriptions, barcode, genres, styles, 1959);
	}

	public static DiscogsReleaseResponse releaseResponse(String artistName, String labelName, String catalogNo,
			List<String> formatDescriptions, String barcode, List<String> genres, List<String> styles,
			Integer year) {
		return releaseResponse(249504L, 21247L, artistName, labelName, catalogNo, formatDescriptions, barcode,
				genres, styles, year);
	}

	/** id/masterId 를 직접 지정한다. 재검증의 병합(리다이렉트)·마스터 변경 시나리오에 쓴다. */
	public static DiscogsReleaseResponse releaseResponse(long id, Long masterId, String artistName, String labelName,
			String catalogNo, List<String> formatDescriptions, String barcode, List<String> genres,
			List<String> styles, Integer year) {
		List<DiscogsReleaseResponse.Artist> artists = artistName == null ? List.of()
				: List.of(new DiscogsReleaseResponse.Artist(artistName));
		List<DiscogsReleaseResponse.Label> labels = labelName == null ? List.of()
				: List.of(new DiscogsReleaseResponse.Label(labelName, catalogNo));
		List<DiscogsReleaseResponse.Format> formats = List.of(new DiscogsReleaseResponse.Format("Vinyl",
				formatDescriptions));
		List<DiscogsReleaseResponse.Identifier> identifiers = barcode == null ? List.of()
				: List.of(new DiscogsReleaseResponse.Identifier("Barcode", barcode));
		List<DiscogsReleaseResponse.Image> images = List.of(
				new DiscogsReleaseResponse.Image("primary", "https://i.discogs.com/large.jpeg",
						"https://i.discogs.com/150.jpeg"));
		return new DiscogsReleaseResponse(id, "Kind Of Blue", artists, labels, "Germany", year, genres, styles,
				formats, identifiers, images, masterId, "Discogs 원본 노트");
	}
}
