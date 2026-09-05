package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropService;
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
import com.groove.recommend.service.ProductViewedEvent;
import com.groove.wishlist.repository.WishlistRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"),
			ZoneId.of("Asia/Seoul"));

	@Mock
	private ProductSearchMapper productSearchMapper;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private ProductImageRepository productImageRepository;

	@Mock
	private StockRepository stockRepository;

	@Mock
	private WishlistRepository wishlistRepository;

	@Mock
	private LimitedDropService limitedDropService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productSearchMapper, productRepository, productImageRepository,
				stockRepository, wishlistRepository, limitedDropService, eventPublisher, FIXED_CLOCK);
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
			productService.search(request, null);

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
			assertThatThrownBy(() -> productService.search(request, null))
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
			PageResponse<ProductSummaryResponse> result = productService.search(request, null);

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
			PageResponse<ProductSummaryResponse> result = productService.search(request, null);

			// then
			assertThat(result.totalElements()).isEqualTo(45L);
			assertThat(result.totalPages()).isEqualTo(3);
			verify(productSearchMapper).searchProducts(any());
		}

		@Test
		@DisplayName("memberId 를 검색 조건에 그대로 전달한다")
		void passesMemberIdIntoCondition() {
			// given
			ProductSearchRequest request = new ProductSearchRequest(null, null, null, null, null, null, null, null,
					null);
			given(productSearchMapper.countProducts(any())).willReturn(0L);
			ArgumentCaptor<ProductSearchCondition> captor = ArgumentCaptor.forClass(ProductSearchCondition.class);

			// when
			productService.search(request, 7L);

			// then
			verify(productSearchMapper).countProducts(captor.capture());
			assertThat(captor.getValue().memberId()).isEqualTo(7L);
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
			ProductDetailResponse response = productService.getDetail(10L, null);

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
		@DisplayName("상세 조회에 성공하면 ProductViewedEvent 를 한 번 발행한다")
		void publishesViewedEventOnSuccess() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 10L);
			given(productRepository.findDetailById(10L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(10L)).willReturn(List.of());
			given(stockRepository.findByProductId(10L)).willReturn(Optional.empty());

			// when
			productService.getDetail(10L, 7L);

			// then
			ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			ProductViewedEvent event = captor.getValue();
			assertThat(event.memberId()).isEqualTo(7L);
			assertThat(event.productId()).isEqualTo(10L);
			assertThat(event.viewedAt()).isEqualTo(LocalDateTime.now(FIXED_CLOCK));
		}

		@Test
		@DisplayName("비로그인 조회면 memberId 가 null 인 이벤트를 발행한다")
		void publishesViewedEventWithNullMemberIdWhenGuest() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 10L);
			given(productRepository.findDetailById(10L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(10L)).willReturn(List.of());
			given(stockRepository.findByProductId(10L)).willReturn(Optional.empty());

			// when
			productService.getDetail(10L, null);

			// then
			ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
			verify(eventPublisher).publishEvent(captor.capture());
			assertThat(captor.getValue().memberId()).isNull();
		}

		@Test
		@DisplayName("평점 집계가 채워져 있으면 평균 평점과 리뷰 개수를 그대로 내려준다")
		void returnsAverageRatingAndReviewCountWhenAggregated() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 11L);
			ReflectionTestUtils.setField(product, "averageRating", new BigDecimal("4.5"));
			ReflectionTestUtils.setField(product, "reviewCount", 3);
			given(productRepository.findDetailById(11L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(11L)).willReturn(List.of());
			given(stockRepository.findByProductId(11L)).willReturn(Optional.empty());

			// when
			ProductDetailResponse response = productService.getDetail(11L, null);

			// then
			assertThat(response.averageRating()).isEqualTo(4.5);
			assertThat(response.reviewCount()).isEqualTo(3L);
		}

		@Test
		@DisplayName("memberId 가 있으면 wishlisted 를 조회해 내려준다")
		void returnsWishlistedWhenMemberIdPresent() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 13L);
			given(productRepository.findDetailById(13L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(13L)).willReturn(List.of());
			given(stockRepository.findByProductId(13L)).willReturn(Optional.empty());
			given(wishlistRepository.existsByMemberIdAndProductId(1L, 13L)).willReturn(true);

			// when
			ProductDetailResponse response = productService.getDetail(13L, 1L);

			// then
			assertThat(response.wishlisted()).isTrue();
		}

		@Test
		@DisplayName("memberId 가 없으면 wishlisted 가 null 이고 위시리스트를 조회하지 않는다")
		void skipsWishlistLookupWhenMemberIdAbsent() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 14L);
			given(productRepository.findDetailById(14L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(14L)).willReturn(List.of());
			given(stockRepository.findByProductId(14L)).willReturn(Optional.empty());

			// when
			ProductDetailResponse response = productService.getDetail(14L, null);

			// then
			assertThat(response.wishlisted()).isNull();
			verify(wishlistRepository, never()).existsByMemberIdAndProductId(any(), any());
		}

		@Test
		@DisplayName("존재하지 않으면 PRODUCT_NOT_FOUND 예외를 던진다")
		void throwsWhenNotFound() {
			// given
			given(productRepository.findDetailById(99L)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> productService.getDetail(99L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
			verify(eventPublisher, never()).publishEvent(any());
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
			assertThatThrownBy(() -> productService.getDetail(11L, null))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.PRODUCT_HIDDEN);
			verify(eventPublisher, never()).publishEvent(any());
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
			ProductDetailResponse response = productService.getDetail(12L, null);

			// then
			assertThat(response.stockQuantity()).isZero();
		}

		@Test
		@DisplayName("활성 한정반이 있으면 limitedDrop 요약을 포함한다")
		void returnsLimitedDropSummaryWhenPresent() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 15L);
			given(productRepository.findDetailById(15L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(15L)).willReturn(List.of());
			given(stockRepository.findByProductId(15L)).willReturn(Optional.empty());
			ProductDetailResponse.LimitedDropSummary summary = new ProductDetailResponse.LimitedDropSummary(
					1L, LimitedDropStatus.OPEN, null, null, 30, 2);
			given(limitedDropService.findSummaryForProduct(15L)).willReturn(Optional.of(summary));

			// when
			ProductDetailResponse response = productService.getDetail(15L, null);

			// then
			assertThat(response.limitedDrop()).isEqualTo(summary);
		}

		@Test
		@DisplayName("활성 한정반이 없으면 limitedDrop 이 null 이다")
		void returnsNullLimitedDropWhenAbsent() {
			// given
			Artist artist = ArtistFixture.withId(artist(), 1L);
			Product product = ProductFixture.withId(ProductFixture.create(artist), 16L);
			given(productRepository.findDetailById(16L)).willReturn(Optional.of(product));
			given(productImageRepository.findAllByProductIdOrderBySortOrderAsc(16L)).willReturn(List.of());
			given(stockRepository.findByProductId(16L)).willReturn(Optional.empty());
			given(limitedDropService.findSummaryForProduct(16L)).willReturn(Optional.empty());

			// when
			ProductDetailResponse response = productService.getDetail(16L, null);

			// then
			assertThat(response.limitedDrop()).isNull();
		}

		private Artist artist() {
			return ArtistFixture.create();
		}
	}
}
