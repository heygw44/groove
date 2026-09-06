package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogService;
import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.inventory.service.StockService;
import com.groove.product.dto.AdminProductResponse;
import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
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
class AdminProductServiceTest {

	private static final Long ADMIN_ID = 1L;
	private static final Long ARTIST_ID = 10L;
	private static final Long LABEL_ID = 20L;
	private static final Long PRODUCT_ID = 100L;

	@Mock
	ProductRepository productRepository;

	@Mock
	AlbumRepository albumRepository;

	@Mock
	ArtistRepository artistRepository;

	@Mock
	LabelRepository labelRepository;

	@Mock
	GenreRepository genreRepository;

	@Mock
	StockRepository stockRepository;

	@Mock
	StockService stockService;

	@Mock
	AdminAuditLogService adminAuditLogService;

	AdminProductService adminProductService;

	@BeforeEach
	void setUp() {
		adminProductService = new AdminProductService(productRepository, albumRepository, artistRepository,
				labelRepository, genreRepository, stockRepository, stockService, adminAuditLogService);
	}

	@Nested
	@DisplayName("create()")
	class Create {

		@Test
		@DisplayName("성공하면 재고를 생성하고 감사 로그를 남긴다")
		void createsProductWithInitialStock() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Label label = LabelFixture.create();
			Album album = AlbumFixture.create(artist);
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, LABEL_ID, List.of());
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.of(artist));
			given(labelRepository.findById(LABEL_ID)).willReturn(Optional.of(label));
			given(albumRepository.findById(1L)).willReturn(Optional.of(album));
			Product saved = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			given(productRepository.save(any())).willReturn(saved);
			Stock stock = StockFixture.create(saved, 10);
			given(stockService.create(any(), anyInt())).willReturn(stock);

			// when
			AdminProductResponse response = adminProductService.create(ADMIN_ID, request);

			// then
			assertThat(response.stockQuantity()).isEqualTo(10);
			verify(stockService).create(saved, request.initialStock());
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_CREATE,
					AdminAuditTargetType.PRODUCT, saved.getId(), null);
		}

		@Test
		@DisplayName("존재하지 않는 아티스트면 ARTIST_NOT_FOUND 예외를 던진다")
		void throwsWhenArtistNotFound() {
			// given
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, null, List.of());
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminProductService.create(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.ARTIST_NOT_FOUND);
			verify(productRepository, never()).save(any());
		}

		@Test
		@DisplayName("장르 id 중 일부가 존재하지 않으면 GENRE_NOT_FOUND 예외를 던진다")
		void throwsWhenSomeGenreNotFound() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Genre jazz = GenreFixture.create("Jazz");
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, null, List.of(1L, 2L));
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.of(artist));
			given(genreRepository.findAllById(any())).willReturn(List.of(jazz));

			// when & then
			assertThatThrownBy(() -> adminProductService.create(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.GENRE_NOT_FOUND);
			verify(productRepository, never()).save(any());
		}

		@Test
		@DisplayName("albumId 와 newAlbum 을 모두 지정하면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenBothAlbumIdAndNewAlbumGiven() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			ProductCreateRequest.NewAlbumRequest newAlbum = new ProductCreateRequest.NewAlbumRequest(
					"Kind of Blue", 1959);
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, null, List.of(), 1L, newAlbum);
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.of(artist));

			// when & then
			assertThatThrownBy(() -> adminProductService.create(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(productRepository, never()).save(any());
		}

		@Test
		@DisplayName("albumId 와 newAlbum 을 둘 다 지정하지 않으면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenNeitherAlbumIdNorNewAlbumGiven() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, null, List.of(), null, null);
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.of(artist));

			// when & then
			assertThatThrownBy(() -> adminProductService.create(ADMIN_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
			verify(productRepository, never()).save(any());
		}

		@Test
		@DisplayName("newAlbum 을 지정하면 새 앨범을 만들어 연결한다")
		void createsNewAlbumWhenNewAlbumGiven() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			ProductCreateRequest.NewAlbumRequest newAlbum = new ProductCreateRequest.NewAlbumRequest(
					"A Love Supreme", 1965);
			ProductCreateRequest request = ProductFixture.createRequest(ARTIST_ID, null, List.of(), null, newAlbum);
			given(artistRepository.findById(ARTIST_ID)).willReturn(Optional.of(artist));
			Album savedAlbum = AlbumFixture.withId(AlbumFixture.create(artist, "A Love Supreme"), 5L);
			given(albumRepository.save(any())).willReturn(savedAlbum);
			Product saved = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			given(productRepository.save(any())).willReturn(saved);
			given(stockService.create(any(), anyInt())).willReturn(StockFixture.create(saved, 10));

			// when
			adminProductService.create(ADMIN_ID, request);

			// then
			verify(albumRepository).save(any());
			verify(albumRepository, never()).findById(any());
		}
	}

	@Nested
	@DisplayName("update()")
	class Update {

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());
			ProductUpdateRequest request = ProductFixture.emptyUpdateRequest();

			// when & then
			assertThatThrownBy(() -> adminProductService.update(ADMIN_ID, PRODUCT_ID, request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("모든 필드가 null 이면 기존 값을 유지하고 genres/images 도 그대로 둔다")
		void keepsExistingValuesWhenAllFieldsNull() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			ProductUpdateRequest request = ProductFixture.emptyUpdateRequest();

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.title()).isEqualTo(product.getTitle());
			assertThat(response.images()).isEmpty();
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_UPDATE,
					AdminAuditTargetType.PRODUCT, PRODUCT_ID, "");
		}

		@Test
		@DisplayName("imageUrls 를 전달하면 기존 이미지를 지우고 새 이미지로 교체한다")
		void replacesImages() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			product.addImage("https://cdn.groove.com/old.jpg", 0);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			ProductUpdateRequest request = ProductFixture.updateRequest(null, null, null);

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.images()).extracting(AdminProductResponse.ImageSummary::url)
					.containsExactly("https://cdn.groove.com/updated.jpg");
		}

		@Test
		@DisplayName("genreIds 가 null 이면 기존 장르를 유지한다")
		void keepsGenresWhenGenreIdsNull() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			Genre jazz = GenreFixture.create("Jazz");
			product.addGenre(jazz);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			ProductUpdateRequest request = ProductFixture.emptyUpdateRequest();

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.genres()).extracting(AdminProductResponse.GenreSummary::name)
					.containsExactly("Jazz");
			verify(genreRepository, never()).findAllById(any());
		}

		@Test
		@DisplayName("labelId 가 명시적으로 null 이면 레이블을 해제한다")
		void clearsLabelWhenLabelIdIsExplicitNull() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist, LabelFixture.create()),
					PRODUCT_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			ProductUpdateRequest request = ProductFixture.updateRequestWithLabel(JsonNullable.of(null));

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.label()).isNull();
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_UPDATE,
					AdminAuditTargetType.PRODUCT, PRODUCT_ID, "label");
			verify(labelRepository, never()).findById(any());
		}

		@Test
		@DisplayName("labelId 키가 없으면 기존 레이블을 유지한다")
		void keepsLabelWhenLabelIdUndefined() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Label label = LabelFixture.create();
			Product product = ProductFixture.withId(ProductFixture.create(artist, label), PRODUCT_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			ProductUpdateRequest request = ProductFixture.emptyUpdateRequest();

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.label().name()).isEqualTo(label.getName());
			verify(labelRepository, never()).findById(any());
		}

		@Test
		@DisplayName("labelId 에 값이 있으면 레이블을 교체한다")
		void replacesLabelWhenLabelIdGiven() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist, LabelFixture.create()),
					PRODUCT_ID);
			Label newLabel = LabelFixture.withId(LabelFixture.create("Impulse!"), LABEL_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));
			given(labelRepository.findById(LABEL_ID)).willReturn(Optional.of(newLabel));
			ProductUpdateRequest request = ProductFixture.updateRequestWithLabel(JsonNullable.of(LABEL_ID));

			// when
			AdminProductResponse response = adminProductService.update(ADMIN_ID, PRODUCT_ID, request);

			// then
			assertThat(response.label().name()).isEqualTo("Impulse!");
		}
	}

	@Nested
	@DisplayName("hide()")
	class Hide {

		@Test
		@DisplayName("상품을 HIDDEN 상태로 바꾸고 감사 로그를 남긴다")
		void hidesProductAndRecordsAudit() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when
			adminProductService.hide(ADMIN_ID, PRODUCT_ID);

			// then
			assertThat(product.isHidden()).isTrue();
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_HIDE,
					AdminAuditTargetType.PRODUCT, PRODUCT_ID, null);
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminProductService.hide(ADMIN_ID, PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("HIDDEN 상품도 조회할 수 있다")
		void returnsHiddenProduct() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			product.hide();
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));

			// when
			AdminProductResponse response = adminProductService.getDetail(PRODUCT_ID);

			// then
			assertThat(response.status()).isEqualTo(product.getStatus());
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminProductService.getDetail(PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("restore()")
	class Restore {

		@Test
		@DisplayName("재고가 남아 있으면 ON_SALE 로 복구하고 감사 로그를 남긴다")
		void restoresAndRecordsAudit() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			product.hide();
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 10)));

			// when
			AdminProductResponse response = adminProductService.restore(ADMIN_ID, PRODUCT_ID);

			// then
			assertThat(response.status()).isEqualTo(ProductStatus.ON_SALE);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_RESTORE,
					AdminAuditTargetType.PRODUCT, PRODUCT_ID, "ON_SALE");
		}

		@Test
		@DisplayName("재고가 0 이면 SOLD_OUT 으로 복구한다")
		void restoresToSoldOutWhenStockIsZero() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			product.hide();
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID))
					.willReturn(Optional.of(StockFixture.create(product, 0)));

			// when
			AdminProductResponse response = adminProductService.restore(ADMIN_ID, PRODUCT_ID);

			// then
			assertThat(response.status()).isEqualTo(ProductStatus.SOLD_OUT);
			verify(adminAuditLogService).record(ADMIN_ID, AdminAuditAction.PRODUCT_RESTORE,
					AdminAuditTargetType.PRODUCT, PRODUCT_ID, "SOLD_OUT");
		}

		@Test
		@DisplayName("HIDDEN 상태가 아니면 PRODUCT_NOT_HIDDEN 예외를 던진다")
		void throwsWhenProductNotHidden() {
			// given
			Artist artist = ArtistFixture.withId(ARTIST_ID);
			Product product = ProductFixture.withId(ProductFixture.create(artist), PRODUCT_ID);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));
			given(stockRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(StockFixture.create(product)));

			// when & then
			assertThatThrownBy(() -> adminProductService.restore(ADMIN_ID, PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_HIDDEN);
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenProductNotFound() {
			// given
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminProductService.restore(ADMIN_ID, PRODUCT_ID))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("getList()")
	class GetList {

		@Test
		@DisplayName("albumId 를 지정하면 그대로 리포지토리에 전달한다")
		void passesAlbumIdIntoRepository() {
			// given
			Long albumId = 5L;
			Pageable pageable = PageRequest.of(0, 20);
			AdminProductSummaryResponse summary = new AdminProductSummaryResponse(PRODUCT_ID, "Kind of Blue",
					"Miles Davis", null, new BigDecimal("45000.00"), ProductStatus.ON_SALE, null, 10, null);
			Page<AdminProductSummaryResponse> page = new PageImpl<>(List.of(summary), pageable, 1);
			given(productRepository.findAdminSummaries(eq(ProductStatus.ON_SALE), eq(albumId), eq(pageable)))
					.willReturn(page);

			// when
			PageResponse<AdminProductSummaryResponse> result = adminProductService.getList(ProductStatus.ON_SALE,
					albumId, pageable);

			// then
			assertThat(result.content()).containsExactly(summary);
			verify(productRepository).findAdminSummaries(ProductStatus.ON_SALE, albumId, pageable);
		}

		@Test
		@DisplayName("albumId 가 없으면 null 로 리포지토리에 전달한다")
		void passesNullAlbumIdWhenAbsent() {
			// given
			Pageable pageable = PageRequest.of(0, 20);
			given(productRepository.findAdminSummaries(isNull(), isNull(), eq(pageable)))
					.willReturn(new PageImpl<>(List.of(), pageable, 0));

			// when
			adminProductService.getList(null, null, pageable);

			// then
			verify(productRepository).findAdminSummaries(null, null, pageable);
		}
	}
}
