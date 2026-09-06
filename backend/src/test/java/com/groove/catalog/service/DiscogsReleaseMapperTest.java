package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.fixture.DiscogsFixture;
import com.groove.product.entity.EditionType;

class DiscogsReleaseMapperTest {

	private final DiscogsReleaseMapper mapper = new DiscogsReleaseMapper();

	@Nested
	@DisplayName("toLookup()")
	class ToLookup {

		@ParameterizedTest
		@CsvSource({
			"'Nirvana - Nevermind', Nirvana, Nevermind",
			"'Kind of Blue', , 'Kind of Blue'"
		})
		@DisplayName("제목을 첫 ' - ' 기준으로 아티스트와 앨범명으로 분리한다")
		void splitsArtistAndTitleBySeparator(String title, String expectedArtist, String expectedTitle) {
			// given
			DiscogsSearchResponse.Result result = DiscogsFixture.searchResult(1L, title, "2015");

			// when
			CatalogLookupResponse response = mapper.toLookup(result, false);

			// then
			assertThat(response.artist()).isEqualTo(expectedArtist);
			assertThat(response.title()).isEqualTo(expectedTitle);
		}

		@ParameterizedTest
		@CsvSource({
			"2015, 2015",
			"'', ",
			"abc, "
		})
		@DisplayName("year 를 정수로 파싱하고 실패하거나 비어 있으면 null 로 매핑한다")
		void mapsYearOrNullWhenUnparseable(String year, Integer expectedYear) {
			// given
			DiscogsSearchResponse.Result result = DiscogsFixture.searchResult(1L, "Title", year);

			// when
			CatalogLookupResponse response = mapper.toLookup(result, false);

			// then
			assertThat(response.year()).isEqualTo(expectedYear);
		}

		@Test
		@DisplayName("alreadyImported 값을 그대로 반영한다")
		void mapsAlreadyImportedFlag() {
			// given
			DiscogsSearchResponse.Result result = DiscogsFixture.searchResult(1L, "Title", "2015");

			// when
			CatalogLookupResponse response = mapper.toLookup(result, true);

			// then
			assertThat(response.alreadyImported()).isTrue();
			assertThat(response.discogsReleaseId()).isEqualTo(1L);
		}
	}

	@Nested
	@DisplayName("toDetail()")
	class ToDetail {

		@ParameterizedTest
		@CsvSource({
			"'Limited Edition', LIMITED",
			"Promo, PROMO",
			"Remastered, REMASTER",
			"Reissue, REISSUE",
			"Repress, REISSUE",
			"'', STANDARD"
		})
		@DisplayName("포맷 설명으로 에디션 타입을 판정한다")
		void resolvesEditionTypeFromFormatDescriptions(String description, EditionType expected) {
			// given
			List<String> descriptions = description.isBlank() ? List.of() : List.of(description);
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					descriptions, null, List.of(), List.of());

			// when
			CatalogReleaseDetailResponse detail = mapper.toDetail(release, List.of("Jazz"));

			// then
			assertThat(detail.editionType()).isEqualTo(expected);
		}

		@ParameterizedTest
		@CsvSource({
			"none, true",
			"None, true",
			"CS 8163, false",
			"'', true"
		})
		@DisplayName("catno 가 none(대소문자 무시)이거나 비어 있으면 null 로 매핑한다")
		void mapsCatalogNoNoneOrBlankToNull(String catalogNo, boolean expectedNull) {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", catalogNo,
					List.of("LP"), null, List.of(), List.of());

			// when
			CatalogReleaseDetailResponse detail = mapper.toDetail(release, List.of());

			// then
			assertThat(detail.catalogNo() == null).isEqualTo(expectedNull);
		}

		@ParameterizedTest
		@CsvSource({
			"'501-239 4144777', 5012394144777",
			"'5012394144777', 5012394144777"
		})
		@DisplayName("바코드에서 공백과 하이픈을 제거한다")
		void stripsWhitespaceAndHyphensFromBarcode(String rawBarcode, String expectedBarcode) {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), rawBarcode, List.of(), List.of());

			// when
			CatalogReleaseDetailResponse detail = mapper.toDetail(release, List.of());

			// then
			assertThat(detail.barcode()).isEqualTo(expectedBarcode);
		}

		@Test
		@DisplayName("genres/styles 중 알고 있는 장르명과 대소문자 무시로 일치하는 것만 우리 표기로 남긴다")
		void keepsOnlyKnownGenresCaseInsensitively() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of("jazz", "Rock"), List.of("Cool Jazz"));

			// when
			CatalogReleaseDetailResponse detail = mapper.toDetail(release, List.of("Jazz", "Pop"));

			// then
			assertThat(detail.genreNames()).containsExactly("Jazz");
		}

		@Test
		@DisplayName("아티스트 이름의 Discogs 중복 접미사 (n) 을 제거한다")
		void stripsDiscogsDuplicateSuffixFromArtistName() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Nirvana (2)", "DGC", "CS 8163",
					List.of("LP"), null, List.of(), List.of());

			// when
			CatalogReleaseDetailResponse detail = mapper.toDetail(release, List.of());

			// then
			assertThat(detail.artistName()).isEqualTo("Nirvana");
		}
	}

	@Nested
	@DisplayName("isVinyl()")
	class IsVinyl {

		@ParameterizedTest
		@ValueSource(strings = {"Vinyl", "vinyl", "VINYL"})
		@DisplayName("formats 중 이름이 Vinyl 이면(대소문자 무시) true 를 반환한다")
		void returnsTrueWhenAnyFormatNameIsVinyl(String formatName) {
			// given
			DiscogsReleaseResponse release = new DiscogsReleaseResponse(1L, "Title", List.of(), List.of(), "US",
					2000, List.of(), List.of(), List.of(new DiscogsReleaseResponse.Format(formatName, List.of())),
					List.of(), List.of(), null, null);

			// when & then
			assertThat(mapper.isVinyl(release)).isTrue();
		}

		@Test
		@DisplayName("Vinyl 포맷이 없으면 false 를 반환한다")
		void returnsFalseWhenNoVinylFormat() {
			// given
			DiscogsReleaseResponse release = new DiscogsReleaseResponse(1L, "Title", List.of(), List.of(), "US",
					2000, List.of(), List.of(), List.of(new DiscogsReleaseResponse.Format("CD", List.of())),
					List.of(), List.of(), null, null);

			// when & then
			assertThat(mapper.isVinyl(release)).isFalse();
		}

		@Test
		@DisplayName("formats 가 null 이면 false 를 반환한다")
		void returnsFalseWhenFormatsIsNull() {
			// given
			DiscogsReleaseResponse release = new DiscogsReleaseResponse(1L, "Title", List.of(), List.of(), "US",
					2000, List.of(), List.of(), null, List.of(), List.of(), null, null);

			// when & then
			assertThat(mapper.isVinyl(release)).isFalse();
		}
	}

	@Nested
	@DisplayName("isVinylFormat()")
	class IsVinylFormat {

		@ParameterizedTest
		@CsvSource({
			"'Vinyl, LP, Album', true",
			"vinyl, true",
			"VINYL, true",
			"CD, false",
			"'', false"
		})
		@DisplayName("format 문자열에 vinyl 이 포함되면(대소문자 무시) true 를 반환한다")
		void returnsTrueWhenFormatContainsVinyl(String versionFormat, boolean expected) {
			// when & then
			assertThat(mapper.isVinylFormat(versionFormat)).isEqualTo(expected);
		}

		@ParameterizedTest
		@NullSource
		@DisplayName("null 이면 false 를 반환한다")
		void returnsFalseWhenNull(String versionFormat) {
			// when & then
			assertThat(mapper.isVinylFormat(versionFormat)).isFalse();
		}
	}

	@Nested
	@DisplayName("isVinylVersion()")
	class IsVinylVersion {

		@Test
		@DisplayName("format 문자열에 Vinyl 이 없어도 major_formats 가 Vinyl 이면 true 를 반환한다")
		void returnsTrueWhenMajorFormatsContainsVinylRegardlessOfFormatString() {
			// given
			Version version = DiscogsFixture.version(1L, List.of("Vinyl"), "LP, Album, Promo");

			// when & then
			assertThat(mapper.isVinylVersion(version)).isTrue();
		}

		@Test
		@DisplayName("major_formats 가 CD 면 format 문자열과 무관하게 false 를 반환한다")
		void returnsFalseWhenMajorFormatsIsCd() {
			// given
			Version version = DiscogsFixture.version(1L, List.of("CD"), "Album");

			// when & then
			assertThat(mapper.isVinylVersion(version)).isFalse();
		}

		@ParameterizedTest
		@CsvSource({
			"'Vinyl, LP', true",
			"Album, false"
		})
		@DisplayName("major_formats 가 없으면 format 문자열 판정으로 폴백한다")
		void fallsBackToFormatStringWhenMajorFormatsIsNull(String format, boolean expected) {
			// given
			Version version = DiscogsFixture.version(1L, null, format);

			// when & then
			assertThat(mapper.isVinylVersion(version)).isEqualTo(expected);
		}

		@Test
		@DisplayName("major_formats 가 빈 목록이면 format 문자열 판정으로 폴백한다")
		void fallsBackToFormatStringWhenMajorFormatsIsEmpty() {
			// given
			Version version = DiscogsFixture.version(1L, List.of(), "Vinyl, LP");

			// when & then
			assertThat(mapper.isVinylVersion(version)).isTrue();
		}
	}

	@Nested
	@DisplayName("toImportItem()")
	class ToImportItem {

		@Test
		@DisplayName("masterIdOverride 가 있으면 release 의 masterId 대신 override 를 사용한다")
		void usesMasterIdOverrideWhenPresent() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of(), List.of());

			// when
			CatalogImportItem item = mapper.toImportItem(release, List.of(), 999L, new BigDecimal("30000"));

			// then
			assertThat(item.discogsMasterId()).isEqualTo(999L);
		}

		@Test
		@DisplayName("masterIdOverride 가 없으면 release 의 masterId 를 사용한다")
		void usesReleaseMasterIdWhenOverrideIsNull() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of(), List.of());

			// when
			CatalogImportItem item = mapper.toImportItem(release, List.of(), null, new BigDecimal("30000"));

			// then
			assertThat(item.discogsMasterId()).isEqualTo(release.masterId());
		}

		@Test
		@DisplayName("알고 있는 장르명만 일치시켜 담는다")
		void matchesOnlyKnownGenreNames() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of("jazz", "Rock"), List.of());

			// when
			CatalogImportItem item = mapper.toImportItem(release, List.of("Jazz"), null, new BigDecimal("30000"));

			// then
			assertThat(item.genreNames()).containsExactly("Jazz");
		}

		@Test
		@DisplayName("price 를 그대로 전달한다")
		void passesThroughPrice() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of(), List.of());
			BigDecimal price = new BigDecimal("45000");

			// when
			CatalogImportItem item = mapper.toImportItem(release, List.of(), null, price);

			// then
			assertThat(item.price()).isEqualTo(price);
		}
	}
}
