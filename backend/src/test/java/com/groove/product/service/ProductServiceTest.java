package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSortType;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductImage;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.ProductImageRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductSearchMapper productSearchMapper;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductImageRepository productImageRepository;

	@Mock
	private StockRepository stockRepository;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productSearchMapper, productRepository, productImageRepository,
				stockRepository);
	}

	@Nested
	@DisplayName("search()")
	class Search {

		@Test
		@DisplayName("page·size 가 없으면 기본값 0·20 으로 조회한다")
		void appliesDefaultPageAndSize() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, null, null, null, null,
					null);
			given(productSearchMapper.countProducts(any())).willReturn(0L);
			ArgumentCaptor<ProductSearchCondition> captor = ArgumentCaptor.forClass(ProductSearchCondition.class);

			// when
			productService.search(request);

			// then
			verify(productSearchMapper).countProducts(captor.capture());
			assertThat(captor.getValue().page()).isZero();
			assertThat(captor.getValue().size()).isEqualTo(20);
			assertThat(captor.getValue().sort()).isEqualTo(ProductSortType.LATEST);
		}

		@Test
		@DisplayName("정렬 값이 잘못되면 COMMON_INVALID_INPUT 예외를 던진다")
		void throwsWhenSortInvalid() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, null, null, "invalid",
					null, null);

			// when & then
			assertThatThrownBy(() -> productService.search(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("전체 개수가 0 이면 목록을 조회하지 않고 빈 결과를 반환한다")
		void returnsEmptyContentWhenCountIsZero() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, null, null, null, null,
					null);
			given(productSearchMapper.countProducts(any())).willReturn(0L);

			// when
			PageResponse<ProductSummaryResponse> result = productService.search(request);

			// then
			assertThat(result.content()).isEmpty();
			assertThat(result.totalElements()).isZero();
			verify(productSearchMapper, never()).searchProducts(any());
		}

		@Test
		@DisplayName("전체 개수가 있으면 목록을 조회하고 totalPages 를 계산한다")
		void returnsPagedContentWhenCountIsPositive() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, null, null, null, 0,
					20);
			given(productSearchMapper.countProducts(any())).willReturn(45L);
			given(productSearchMapper.searchProducts(any())).willReturn(List.of());

			// when
			PageResponse<ProductSummaryResponse> result = productService.search(request);

			// then
			assertThat(result.totalElements()).isEqualTo(45L);
			assertThat(result.totalPages()).isEqualTo(3);
			verify(productSearchMapper).searchProducts(any());
		}
	}

	@Nested
	@DisplayName("getDetail()")
	class GetDetail {

		@Test
		@DisplayName("존재하고 판매 중인 상품이면 이미지·재고·장르를 포함한 상세를 반환한다")
		void returnsDetailWithImagesAndStock() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 10L);
			Genre genre = GenreFixture.create("Jazz");
			product.addGenre(genre);
			ProductImage first = ProductImage.of(product, "https://cdn.groove.com/0.jpg", 0);
			ProductImage second = ProductImage.of(product, "https://cdn.groove.com/1.jpg", 1);
			Stock stock = StockFixture.create(product, 5);
			given(productRepository.findDetailById(10L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(10L))
					.willReturn(List.of(first, second));
			given(stockRepository.findByProductId(10L)).willReturn(Optional.of(stock));

			// when
			ProductDetailResponse response = productService.getDetail(10L);

			// then
			assertThat(response.id()).isEqualTo(10L);
			assertThat(response.images()).extracting(ProductDetailResponse.ImageSummary::sortOrder)
					.containsExactly(0, 1);
			assertThat(response.stockQuantity()).isEqualTo(5);
			assertThat(response.genres()).extracting(ProductDetailResponse.GenreSummary::name)
					.containsExactly("Jazz");
			assertThat(response.averageRating()).isNull();
			assertThat(response.reviewCount()).isZero();
		}

		@Test
		@DisplayName("존재하지 않으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(productRepository.findDetailById(99L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> productService.getDetail(99L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
		}

		@Test
		@DisplayName("HIDDEN 상품이면 PRODUCT_HIDDEN 예외를 던진다")
		void throwsWhenHidden() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 11L);
			product.hide();
			given(productRepository.findDetailById(11L)).willReturn(Optional.of(product));

			// when & then
			assertThatThrownBy(() -> productService.getDetail(11L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
		}

		@Test
		@DisplayName("재고가 없으면 stockQuantity 를 0 으로 반환한다")
		void returnsZeroStockWhenStockMissing() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 12L);
			given(productRepository.findDetailById(12L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(12L)).willReturn(List.of());
			given(stockRepository.findByProductId(12L)).willReturn(Optional.empty());

			// when
			ProductDetailResponse response = productService.getDetail(12L);

			// then
			assertThat(response.stockQuantity()).isZero();
		}

		private Artist artist() {
			return ArtistFixture.create();
		}
	}
}
