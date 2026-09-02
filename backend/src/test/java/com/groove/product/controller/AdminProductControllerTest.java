package com.groove.product.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.ProductFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.AdminProductResponse;
import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.dto.ProductCreateRequest;
import com.groove.product.dto.ProductUpdateRequest;
import com.groove.product.entity.ProductStatus;
import com.groove.product.service.AdminProductService;

@WebMvcTest(AdminProductController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminProductControllerTest {

	private static final Long PRODUCT_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminProductService adminProductService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private AdminProductResponse sampleResponse() {
		return new AdminProductResponse(PRODUCT_ID, "Kind of Blue",
				new AdminProductResponse.ArtistSummary(10L, "Miles Davis"), null, List.of(), LocalDate.of(2024, 1, 1),
				"180g", "Black", new BigDecimal("45000.00"), ProductStatus.ON_SALE, "설명", List.of(), 10, null, null);
	}

	@Nested
	@DisplayName("POST /api/v1/admin/products")
	class Create {

		@Test
		@DisplayName("관리자면 201 과 등록된 상품 정보를 반환한다")
		void createsProductWhenAdmin() throws Exception {
			// given
			ProductCreateRequest request = ProductFixture.createRequest(10L, null, List.of());
			given(adminProductService.create(eq(1L), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.title", is("Kind of Blue")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			ProductCreateRequest request = ProductFixture.createRequest(10L, null, List.of());

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminProductService, never()).create(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			ProductCreateRequest request = ProductFixture.createRequest(10L, null, List.of());

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminProductService, never()).create(any(), any());
		}

		@Test
		@DisplayName("제목이 비어 있으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenTitleBlank() throws Exception {
			// given
			ProductCreateRequest request = new ProductCreateRequest("", 10L, null, List.of(),
					LocalDate.of(2024, 1, 1), "180g", "Black", new BigDecimal("45000.00"), "설명", List.of(), 10);

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("title")));
		}

		@Test
		@DisplayName("초기 재고 수량이 음수면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenInitialStockNegative() throws Exception {
			// given
			ProductCreateRequest request = new ProductCreateRequest("Kind of Blue", 10L, null, List.of(),
					LocalDate.of(2024, 1, 1), "180g", "Black", new BigDecimal("45000.00"), "설명", List.of(), -1);

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("initialStock")));
		}

		@Test
		@DisplayName("imageUrls 원소가 비어 있으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenImageUrlBlank() throws Exception {
			// given
			ProductCreateRequest request = new ProductCreateRequest("Kind of Blue", 10L, null, List.of(),
					LocalDate.of(2024, 1, 1), "180g", "Black", new BigDecimal("45000.00"), "설명", List.of(""), 10);

			// when & then
			mockMvc.perform(post("/api/v1/admin/products")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("imageUrls[0]")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/products/{id}")
	class Update {

		@Test
		@DisplayName("관리자면 200 과 수정된 상품 정보를 반환한다")
		void updatesProductWhenAdmin() throws Exception {
			// given
			ProductUpdateRequest request = ProductFixture.updateRequest(null, null, null);
			given(adminProductService.update(eq(1L), eq(PRODUCT_ID), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{id}", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(PRODUCT_ID.intValue())));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			ProductUpdateRequest request = ProductFixture.updateRequest(null, null, null);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{id}", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminProductService, never()).update(any(), any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			ProductUpdateRequest request = ProductFixture.updateRequest(null, null, null);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{id}", PRODUCT_ID)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminProductService, never()).update(any(), any(), any());
		}

		@Test
		@DisplayName("존재하지 않는 상품이면 404 PRODUCT_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenProductMissing() throws Exception {
			// given
			ProductUpdateRequest request = ProductFixture.updateRequest(null, null, null);
			given(adminProductService.update(eq(1L), eq(PRODUCT_ID), any()))
					.willThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

			// when & then
			mockMvc.perform(patch("/api/v1/admin/products/{id}", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/admin/products/{id}")
	class Hide {

		@Test
		@DisplayName("관리자면 200 을 반환하고 상품을 숨긴다")
		void hidesProductWhenAdmin() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/products/{id}", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk());
			verify(adminProductService).hide(1L, PRODUCT_ID);
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/products/{id}", PRODUCT_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminProductService, never()).hide(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/products/{id}", PRODUCT_ID))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminProductService, never()).hide(any(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/products")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 페이지 응답을 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			AdminProductSummaryResponse summary = new AdminProductSummaryResponse(PRODUCT_ID, "Kind of Blue",
					"Miles Davis", null, new BigDecimal("45000.00"), ProductStatus.ON_SALE,
					"https://cdn.groove.com/0.jpg", 10, null);
			PageResponse<AdminProductSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
			given(adminProductService.getList(any(), any())).willReturn(pageResponse);

			// when & then
			mockMvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(PRODUCT_ID.intValue())));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminProductService, never()).getList(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/products"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminProductService, never()).getList(any(), any());
		}
	}
}
