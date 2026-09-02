package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.product.dto.ProductSearchCondition;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSortType;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.mapper.ProductSearchMapper;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

	@Mock
	private ProductSearchMapper productSearchMapper;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productSearchMapper);
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
}
