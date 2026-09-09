package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.catalog.dto.CatalogImportItem;
import com.groove.catalog.dto.CatalogImportResult;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.ProductFixture;
import com.groove.inventory.service.StockService;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.product.entity.EditionType;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CatalogImportRegistrarTest {

	@Mock
	private ArtistRepository artistRepository;

	@Mock
	private LabelRepository labelRepository;

	@Mock
	private AlbumRepository albumRepository;

	@Mock
	private GenreRepository genreRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private StockService stockService;

	private CatalogImportRegistrar registrar;

	private Clock clock;

	@Captor
	private ArgumentCaptor<Product> productCaptor;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-09T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		registrar = new CatalogImportRegistrar(artistRepository, labelRepository, albumRepository, genreRepository,
				productRepository, stockService, clock);
	}

	private CatalogImportItem item(Long discogsMasterId) {
		return new CatalogImportItem(123L, discogsMasterId, "Kind of Blue", "Miles Davis", "Columbia", "US", 1959,
				"CS 8163", "888880123456", EditionType.STANDARD, List.of("Jazz", "Unknown"),
				new BigDecimal("45000"));
	}

	@Nested
	@DisplayName("register()")
	class Register {

		@Test
		@DisplayName("masterId 로 기존 앨범을 찾으면 아티스트·레이블·앨범을 재사용한다")
		void reusesExistingArtistLabelAlbumByMasterId() {
			// given
			CatalogImportItem item = item(21247L);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(GenreFixture.create("Jazz")));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			Product saved = ProductFixture.withId(Product.createImported(album, item.title(), artist, label,
					item.country(), item.pressingYear(), item.catalogNo(), item.barcode(), item.editionType(),
					item.price(), item.discogsReleaseId(), LocalDateTime.now(clock)), 10L);
			given(productRepository.save(any(Product.class))).willReturn(saved);

			// when
			CatalogImportResult result = registrar.register(item);

			// then
			verify(artistRepository, never()).save(any());
			verify(labelRepository, never()).save(any());
			verify(albumRepository, never()).save(any());
			assertThat(result.productId()).isEqualTo(10L);
			assertThat(result.albumId()).isEqualTo(3L);
			assertThat(result.albumTitle()).isEqualTo(album.getTitle());
		}

		@Test
		@DisplayName("일치하는 항목이 없으면 아티스트·레이블·앨범을 새로 만들고 마스터 id 를 연결한다")
		void createsArtistLabelAlbumWhenAbsent() {
			// given
			CatalogImportItem item = item(21247L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.empty());
			Artist newArtist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			given(artistRepository.save(any(Artist.class))).willReturn(newArtist);
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.empty());
			Label newLabel = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			given(labelRepository.save(any(Label.class))).willReturn(newLabel);
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.empty());
			Album newAlbum = AlbumFixture.withId(AlbumFixture.create(newArtist), 3L);
			given(albumRepository.save(any(Album.class))).willReturn(newAlbum);
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(GenreFixture.create("Jazz")));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			ArgumentCaptor<Album> albumCaptor = ArgumentCaptor.forClass(Album.class);
			verify(albumRepository).save(albumCaptor.capture());
			assertThat(albumCaptor.getValue().getDiscogsMasterId()).isEqualTo(21247L);
		}

		@Test
		@DisplayName("masterId 가 없으면 제목과 아티스트로 기존 앨범을 찾는다")
		void fallsBackToTitleAndArtistLookupWhenMasterIdNull() {
			// given
			CatalogImportItem item = item(null);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findFirstByTitleAndArtistIdOrderByIdAsc("Kind of Blue", 1L))
					.willReturn(Optional.of(album));
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(GenreFixture.create("Jazz")));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			verify(albumRepository, never()).findByDiscogsMasterId(any());
			verify(albumRepository, never()).save(any());
		}

		@Test
		@DisplayName("상품을 HIDDEN 상태와 discogsReleaseId 로 생성하고 재고 0 으로 초기화한다")
		void createsHiddenProductWithZeroStock() {
			// given
			CatalogImportItem item = item(21247L);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(GenreFixture.create("Jazz")));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			verify(productRepository).save(productCaptor.capture());
			Product captured = productCaptor.getValue();
			assertThat(captured.getStatus()).isEqualTo(ProductStatus.HIDDEN);
			assertThat(captured.getDiscogsReleaseId()).isEqualTo(123L);
			assertThat(captured.getDiscogsSyncedAt()).isEqualTo(LocalDateTime.now(clock));
			verify(stockService).create(any(Product.class), eq(0));
		}

		@Test
		@DisplayName("알 수 없는 장르명은 무시한다")
		void ignoresUnknownGenreNames() {
			// given
			CatalogImportItem item = item(21247L);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			Genre jazz = GenreFixture.withId(GenreFixture.create("Jazz"), 5L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(jazz));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			verify(productRepository).save(productCaptor.capture());
			List<String> genreNames = productCaptor.getValue().getProductGenres().stream()
					.map(pg -> pg.getGenre().getName())
					.toList();
			assertThat(genreNames).containsExactly("Jazz");
		}

		@Test
		@DisplayName("genreNames 가 null 이면 장르 연결을 건너뛴다")
		void skipsGenreLinkingWhenGenreNamesIsNull() {
			// given
			CatalogImportItem item = new CatalogImportItem(123L, 21247L, "Kind of Blue", "Miles Davis", "Columbia",
					"US", 1959, "CS 8163", "888880123456", EditionType.STANDARD, null, new BigDecimal("45000"));
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			verify(genreRepository, never()).findByName(any());
			verify(productRepository).save(productCaptor.capture());
			assertThat(productCaptor.getValue().getProductGenres()).isEmpty();
		}

		@Test
		@DisplayName("레이블명이 비어 있으면 레이블 없이 상품을 만든다")
		void createsProductWithoutLabelWhenLabelNameIsBlank() {
			// given
			CatalogImportItem item = new CatalogImportItem(123L, 21247L, "Kind of Blue", "Miles Davis", "  ", "US",
					1959, "CS 8163", "888880123456", EditionType.STANDARD, List.of(), new BigDecimal("45000"));
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Album album = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(albumRepository.findByDiscogsMasterId(21247L)).willReturn(Optional.of(album));
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			verify(labelRepository, never()).findFirstByNameOrderByIdAsc(any());
			verify(productRepository).save(productCaptor.capture());
			assertThat(productCaptor.getValue().getLabel()).isNull();
		}

		@Test
		@DisplayName("masterId 가 없고 제목/아티스트로도 앨범을 찾지 못하면 마스터 연결 없이 새 앨범을 만든다")
		void createsAlbumWithoutMasterLinkWhenMasterIdNullAndNotFound() {
			// given
			CatalogImportItem item = item(null);
			Artist artist = ArtistFixture.withId(ArtistFixture.create("Miles Davis"), 1L);
			Label label = LabelFixture.withId(LabelFixture.create("Columbia"), 2L);
			given(artistRepository.findFirstByNameOrderByIdAsc("Miles Davis")).willReturn(Optional.of(artist));
			given(labelRepository.findFirstByNameOrderByIdAsc("Columbia")).willReturn(Optional.of(label));
			given(albumRepository.findFirstByTitleAndArtistIdOrderByIdAsc("Kind of Blue", 1L))
					.willReturn(Optional.empty());
			Album newAlbum = AlbumFixture.withId(AlbumFixture.create(artist), 3L);
			given(albumRepository.save(any(Album.class))).willReturn(newAlbum);
			given(genreRepository.findByName("Jazz")).willReturn(Optional.of(GenreFixture.create("Jazz")));
			given(genreRepository.findByName("Unknown")).willReturn(Optional.empty());
			given(productRepository.save(any(Product.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			registrar.register(item);

			// then
			ArgumentCaptor<Album> albumCaptor = ArgumentCaptor.forClass(Album.class);
			verify(albumRepository).save(albumCaptor.capture());
			assertThat(albumCaptor.getValue().getDiscogsMasterId()).isNull();
		}
	}
}
