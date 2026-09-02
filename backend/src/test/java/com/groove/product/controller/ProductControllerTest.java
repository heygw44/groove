package com.groove.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.product.dto.ProductDetailResponse;
import com.groove.product.dto.ProductSearchRequest;
import com.groove.product.dto.ProductSummaryResponse;
import com.groove.product.entity.ProductStatus;
import com.groove.product.service.ProductService;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class ProductControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductService productService;

	private ProductSummaryResponse sampleSummary() {
		return new ProductSummaryResponse(1L, "Kind of Blue", "Miles Davis", "Columbia", new BigDecimal("45000.00"),
				"Standard Black", "180g Heavyweight Vinyl", ProductStatus.ON_SALE,
				"https://cdn.groove.com/kind-of-blue.jpg", null, 0);
	}

	@Nested
	@DisplayName("GET /api/v1/products")
	class Search {

		@Test
		@DisplayName("비로그인 상태로도 200 과 상품 목록을 반환한다")
		void returnsPageWithoutAuthentication() throws Exception {
			// given
			PageResponse<ProductSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(sampleSummary()), PageRequest.of(0, 20), 1));
			given(productService.search(any())).willReturn(pageResponse);

			// when & then
			mockMvc.perform(get("/api/v1/products"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].title", is("Kind of Blue")));
		}

		@Test
		@DisplayName("size 가 100 을 초과하면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenSizeTooLarge() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/products").param("size", "101"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}

		@Test
		@DisplayName("page 가 음수면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenPageNegative() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/products").param("page", "-1"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}

		@Test
		@DisplayName("쿼리 파라미터가 ProductSearchRequest 로 바인딩된다")
		void bindsQueryParametersToRequest() throws Exception {
			// given
			PageResponse<ProductSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
			given(productService.search(any())).willReturn(pageResponse);
			ArgumentCaptor<ProductSearchRequest> captor = ArgumentCaptor.forClass(ProductSearchRequest.class);

			// when
			mockMvc.perform(get("/api/v1/products")
							.param("keyword", "Kind of Blue")
							.param("genreId", "3")
							.param("sort", "priceAsc"))
					.andExpect(status().isOk());

			// then
			verify(productService).search(captor.capture());
			ProductSearchRequest captured = captor.getValue();
			assertThat(captured.keyword()).isEqualTo("Kind of Blue");
			assertThat(captured.genreId()).isEqualTo(3L);
			assertThat(captured.sort()).isEqualTo("priceAsc");
		}
	}

	@Nested
	@DisplayName("GET /api/v1/products/{id}")
	class GetDetail {

		@Test
		@DisplayName("비로그인 상태로도 200 과 상품 상세를 반환한다")
		void returnsDetailWithoutAuthentication() throws Exception {
			// given
			ProductDetailResponse response = new ProductDetailResponse(1L, "Kind of Blue",
					new ProductDetailResponse.ArtistSummary(12L, "Miles Davis"),
					new ProductDetailResponse.LabelSummary(7L, "Columbia"),
					List.of(new ProductDetailResponse.GenreSummary(3L, "Jazz")),
					null, "180g", "Standard Black", new BigDecimal("45000.00"), ProductStatus.ON_SALE, "설명",
					List.of(new ProductDetailResponse.ImageSummary("https://cdn.groove.com/0.jpg", 0)),
					10, null, 0L);
			given(productService.getDetail(1L)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/products/{id}", 1L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(1)));
		}

		@Test
		@DisplayName("HIDDEN 상품이면 404 PRODUCT_HIDDEN 을 반환한다")
		void returnsNotFoundWhenHidden() throws Exception {
			// given
			given(productService.getDetail(2L)).willThrow(new BusinessException(ErrorCode.PRODUCT_HIDDEN));

			// when & then
			mockMvc.perform(get("/api/v1/products/{id}", 2L))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("PRODUCT_HIDDEN")));
		}
	}
}
