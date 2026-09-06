package com.groove.catalog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsMasterVersionsResponse.Version;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.service.DiscogsReleaseMapper;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

class DiscogsReleaseEnrichProcessorTest {

	private static final long MASTER_ID = 21247L;
	private static final BigDecimal DEFAULT_PRICE = BigDecimal.valueOf(30000);

	private final PressingLookupClient client = mock(PressingLookupClient.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final GenreRepository genreRepository = mock(GenreRepository.class);
	private final DiscogsReleaseMapper mapper = mock(DiscogsReleaseMapper.class);

	private final DiscogsReleaseEnrichProcessor processor = new DiscogsReleaseEnrichProcessor(client,
			productRepository, genreRepository, mapper, MASTER_ID, DEFAULT_PRICE);

	@Nested
	@DisplayName("process()")
	class Process {

		@Test
		@DisplayName("바이닐 포맷이 아니면 상세 조회 없이 null 을 반환한다")
		void returnsNullWithoutFetchingWhenNotVinylFormat() {
			// given
			Version version = DiscogsFixture.version(1L, "CD");
			when(mapper.isVinylVersion(version)).thenReturn(false);

			// when
			CatalogImportItem result = processor.process(version);

			// then
			assertThat(result).isNull();
			verify(client, never()).getRelease(anyLong());
		}

		@Test
		@DisplayName("이미 등록된 릴리즈면 상세 조회 없이 null 을 반환한다")
		void returnsNullWithoutFetchingWhenAlreadyImported() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(productRepository.existsByDiscogsReleaseId(1L)).thenReturn(true);

			// when
			CatalogImportItem result = processor.process(version);

			// then
			assertThat(result).isNull();
			verify(client, never()).getRelease(anyLong());
		}

		@Test
		@DisplayName("릴리즈를 찾을 수 없으면 CatalogItemException 을 던진다")
		void throwsCatalogItemExceptionWhenNotFound() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenThrow(new BusinessException(ErrorCode.CATALOG_RELEASE_NOT_FOUND));

			// when & then
			assertThatThrownBy(() -> processor.process(version))
					.isInstanceOf(CatalogItemException.class)
					.extracting("discogsReleaseId")
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("레이트리밋이면 CatalogTransientException 을 던진다")
		void throwsCatalogTransientExceptionWhenRateLimited() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenThrow(new BusinessException(ErrorCode.CATALOG_RATE_LIMITED));

			// when & then
			assertThatThrownBy(() -> processor.process(version))
					.isInstanceOf(CatalogTransientException.class)
					.extracting("discogsReleaseId")
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("조회 실패면 CatalogTransientException 을 던진다")
		void throwsCatalogTransientExceptionWhenLookupFailed() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenThrow(new BusinessException(ErrorCode.CATALOG_LOOKUP_FAILED));

			// when & then
			assertThatThrownBy(() -> processor.process(version))
					.isInstanceOf(CatalogTransientException.class)
					.extracting("discogsReleaseId")
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("그 외 BusinessException 은 그대로 다시 던진다")
		void rethrowsOtherBusinessException() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenThrow(new BusinessException(ErrorCode.CATALOG_ALREADY_IMPORTED));

			// when & then
			assertThatThrownBy(() -> processor.process(version))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_ALREADY_IMPORTED);
		}

		@Test
		@DisplayName("바이닐 릴리즈가 아니면 null 을 반환한다")
		void returnsNullWhenReleaseIsNotVinyl() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "123", List.of("Jazz"), List.of());
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenReturn(release);
			when(mapper.isVinyl(release)).thenReturn(false);

			// when
			CatalogImportItem result = processor.process(version);

			// then
			assertThat(result).isNull();
		}

		@Test
		@DisplayName("아티스트명이 비어 있으면 CatalogItemException 을 던진다")
		void throwsCatalogItemExceptionWhenArtistBlank() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "123", List.of("Jazz"), List.of());
			CatalogImportItem item = new CatalogImportItem(1L, MASTER_ID, "Kind Of Blue", " ", "Columbia", "US",
					1959, "CS 8163", "123", EditionType.STANDARD, List.of("Jazz"), DEFAULT_PRICE);
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenReturn(release);
			when(mapper.isVinyl(release)).thenReturn(true);
			when(mapper.toImportItem(any(), any(), any(), any())).thenReturn(item);

			// when & then
			assertThatThrownBy(() -> processor.process(version))
					.isInstanceOf(CatalogItemException.class)
					.extracting("discogsReleaseId")
					.isEqualTo(1L);
		}

		@Test
		@DisplayName("정상 케이스면 마스터 id 와 가격이 채워진 아이템을 반환한다")
		void returnsItemOnHappyPath() {
			// given
			Version version = DiscogsFixture.version(1L, "Album");
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "123", List.of("Jazz"), List.of());
			CatalogImportItem item = new CatalogImportItem(1L, MASTER_ID, "Kind Of Blue", "Miles Davis", "Columbia",
					"US", 1959, "CS 8163", "123", EditionType.STANDARD, List.of("Jazz"), DEFAULT_PRICE);
			when(mapper.isVinylVersion(version)).thenReturn(true);
			when(client.getRelease(1L)).thenReturn(release);
			when(mapper.isVinyl(release)).thenReturn(true);
			List<Genre> genres = List.of(genreOf("Jazz"));
			when(genreRepository.findAllByOrderByNameAsc()).thenReturn(genres);
			when(mapper.toImportItem(release, List.of("Jazz"), MASTER_ID, DEFAULT_PRICE)).thenReturn(item);

			// when
			CatalogImportItem result = processor.process(version);

			// then
			assertThat(result).isEqualTo(item);
			assertThat(result.discogsMasterId()).isEqualTo(MASTER_ID);
			assertThat(result.price()).isEqualTo(DEFAULT_PRICE);
		}

		@Test
		@DisplayName("장르 목록은 여러 번 처리해도 한 번만 조회한다")
		void loadsGenreNamesOnce() {
			// given
			Version version1 = DiscogsFixture.version(1L, "Album");
			Version version2 = DiscogsFixture.version(2L, "Album");
			DiscogsReleaseResponse release1 = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "123", List.of("Jazz"), List.of());
			DiscogsReleaseResponse release2 = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), "124", List.of("Jazz"), List.of());
			CatalogImportItem item1 = new CatalogImportItem(1L, MASTER_ID, "Kind Of Blue", "Miles Davis", "Columbia",
					"US", 1959, "CS 8163", "123", EditionType.STANDARD, List.of("Jazz"), DEFAULT_PRICE);
			CatalogImportItem item2 = new CatalogImportItem(2L, MASTER_ID, "Kind Of Blue", "Miles Davis", "Columbia",
					"US", 1959, "CS 8163", "124", EditionType.STANDARD, List.of("Jazz"), DEFAULT_PRICE);
			when(mapper.isVinylVersion(any())).thenReturn(true);
			when(client.getRelease(1L)).thenReturn(release1);
			when(client.getRelease(2L)).thenReturn(release2);
			when(mapper.isVinyl(any())).thenReturn(true);
			List<Genre> genres = List.of(genreOf("Jazz"));
			when(genreRepository.findAllByOrderByNameAsc()).thenReturn(genres);
			when(mapper.toImportItem(release1, List.of("Jazz"), MASTER_ID, DEFAULT_PRICE)).thenReturn(item1);
			when(mapper.toImportItem(release2, List.of("Jazz"), MASTER_ID, DEFAULT_PRICE)).thenReturn(item2);

			// when
			processor.process(version1);
			processor.process(version2);

			// then
			verify(genreRepository, times(1)).findAllByOrderByNameAsc();
		}

		private Genre genreOf(String name) {
			Genre genre = mock(Genre.class);
			when(genre.getName()).thenReturn(name);
			return genre;
		}
	}
}
