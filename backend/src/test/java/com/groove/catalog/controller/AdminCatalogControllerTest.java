package com.groove.catalog.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.catalog.dto.CatalogImportJobRequest;
import com.groove.catalog.dto.CatalogImportJobResponse;
import com.groove.catalog.dto.CatalogImportJobStartResponse;
import com.groove.catalog.dto.CatalogImportRequest;
import com.groove.catalog.dto.CatalogImportResponse;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.catalog.service.CatalogImportJobService;
import com.groove.catalog.service.CatalogImportService;
import com.groove.catalog.service.CatalogLookupService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.entity.EditionType;

@WebMvcTest(AdminCatalogController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminCatalogControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	CatalogLookupService catalogLookupService;

	@MockitoBean
	CatalogImportService catalogImportService;

	@MockitoBean
	CatalogImportJobService catalogImportJobService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/catalog/lookup")
	class Lookup {

		@Test
		@DisplayName("관리자면 200 과 검색 결과를 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			CatalogLookupResponse response = new CatalogLookupResponse(249504L, "Kind Of Blue", "Miles Davis", 1959,
					"US", "CS 8163", "Columbia", "https://thumb", false);
			given(catalogLookupService.lookup(any())).willReturn(PageResponse.of(List.of(response), 0, 20, 1));

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/lookup")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("query", "kind of blue"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].discogsReleaseId", is(249504)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/lookup")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.param("query", "kind of blue"))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogLookupService, never()).lookup(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/lookup").param("query", "kind of blue"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(catalogLookupService, never()).lookup(any());
		}

		@Test
		@DisplayName("검색 조건이 전부 없으면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenNoCriteriaGiven() throws Exception {
			// given
			given(catalogLookupService.lookup(any())).willThrow(new BusinessException(ErrorCode.COMMON_INVALID_INPUT));

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/lookup").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/catalog/releases/{discogsReleaseId}")
	class GetRelease {

		@Test
		@DisplayName("관리자면 200 과 상세 정보를 반환한다")
		void returnsDetailWhenAdmin() throws Exception {
			// given
			CatalogReleaseDetailResponse detail = new CatalogReleaseDetailResponse(249504L, 21247L, "Kind Of Blue",
					"Miles Davis", "Columbia", "US", 1959, "CS 8163", null, EditionType.ORIGINAL, List.of("Jazz"),
					"https://image", "설명");
			given(catalogLookupService.getRelease(249504L)).willReturn(detail);

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/releases/{discogsReleaseId}", 249504L)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.discogsReleaseId", is(249504)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/releases/{discogsReleaseId}", 249504L)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogLookupService, never()).getRelease(249504L);
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/releases/{discogsReleaseId}", 249504L))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(catalogLookupService, never()).getRelease(249504L);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/catalog/imports")
	class ImportRelease {

		@Test
		@DisplayName("관리자면 201 과 등록된 상품/앨범 id 를 반환한다")
		void returnsCreatedWhenAdmin() throws Exception {
			// given
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));
			given(catalogImportService.importRelease(anyLong(), any()))
					.willReturn(new CatalogImportResponse(733L, 310L));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/imports")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.productId", is(733)))
					.andExpect(jsonPath("$.data.albumId", is(310)));
		}

		@Test
		@DisplayName("이미 등록된 릴리즈면 409 CATALOG_ALREADY_IMPORTED 를 반환한다")
		void returnsConflictWhenAlreadyImported() throws Exception {
			// given
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));
			given(catalogImportService.importRelease(anyLong(), any()))
					.willThrow(new BusinessException(ErrorCode.CATALOG_ALREADY_IMPORTED));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/imports")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("CATALOG_ALREADY_IMPORTED")));
		}

		@Test
		@DisplayName("필수값이 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenMissingId() throws Exception {
			// given
			CatalogImportRequest request = new CatalogImportRequest(null, BigDecimal.valueOf(45000));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/imports")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(catalogImportService, never()).importRelease(anyLong(), any());
		}

		@Test
		@DisplayName("가격에 소수부가 있으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenPriceHasFraction() throws Exception {
			// given
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000.5));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/imports")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(catalogImportService, never()).importRelease(anyLong(), any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			CatalogImportRequest request = new CatalogImportRequest(249504L, BigDecimal.valueOf(45000));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/imports")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogImportService, never()).importRelease(anyLong(), any());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/catalog/import-jobs")
	class StartImportJob {

		@Test
		@DisplayName("관리자면 201 과 잡 실행 id 를 반환한다")
		void returnsCreatedWhenAdmin() throws Exception {
			// given
			CatalogImportJobRequest request = new CatalogImportJobRequest(21247L, BigDecimal.valueOf(45000));
			given(catalogImportJobService.start(anyLong(), any())).willReturn(new CatalogImportJobStartResponse(88L));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.jobExecutionId", is(88)));
		}

		@Test
		@DisplayName("필수값이 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenMissingId() throws Exception {
			// given
			CatalogImportJobRequest request = new CatalogImportJobRequest(null, BigDecimal.valueOf(45000));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(catalogImportJobService, never()).start(anyLong(), any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// given
			CatalogImportJobRequest request = new CatalogImportJobRequest(21247L, BigDecimal.valueOf(45000));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogImportJobService, never()).start(anyLong(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/catalog/import-jobs")
	class GetImportJobs {

		@Test
		@DisplayName("관리자면 200 과 적재 작업 목록을 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			CatalogImportJobResponse response = new CatalogImportJobResponse(88L, 21247L, BatchStatus.STARTED, 12, 10,
					1, 1, LocalDateTime.of(2026, 9, 6, 10, 0), null, null);
			given(catalogImportJobService.list(anyInt(), anyInt()))
					.willReturn(PageResponse.of(List.of(response), 0, 20, 1));

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/import-jobs")
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].jobExecutionId", is(88)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/import-jobs")
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogImportJobService, never()).list(anyInt(), anyInt());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/catalog/import-jobs/{jobExecutionId}")
	class GetImportJob {

		@Test
		@DisplayName("관리자면 200 과 상세 정보를 반환한다")
		void returnsDetailWhenAdmin() throws Exception {
			// given
			CatalogImportJobResponse response = new CatalogImportJobResponse(88L, 21247L, BatchStatus.COMPLETED, 12,
					12, 0, 0, LocalDateTime.of(2026, 9, 6, 10, 0), LocalDateTime.of(2026, 9, 6, 10, 5), null);
			given(catalogImportJobService.get(88L)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/import-jobs/{jobExecutionId}", 88L)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("COMPLETED")));
		}

		@Test
		@DisplayName("존재하지 않으면 404 CATALOG_IMPORT_JOB_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenMissing() throws Exception {
			// given
			given(catalogImportJobService.get(999L))
					.willThrow(new BusinessException(ErrorCode.CATALOG_IMPORT_JOB_NOT_FOUND));

			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/import-jobs/{jobExecutionId}", 999L)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("CATALOG_IMPORT_JOB_NOT_FOUND")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/catalog/import-jobs/{jobExecutionId}", 88L)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogImportJobService, never()).get(88L);
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/catalog/import-jobs/{jobExecutionId}/restart")
	class RestartImportJob {

		@Test
		@DisplayName("관리자면 200 과 새 잡 실행 id 를 반환한다")
		void returnsOkWhenAdmin() throws Exception {
			// given
			given(catalogImportJobService.restart(88L)).willReturn(new CatalogImportJobStartResponse(89L));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs/{jobExecutionId}/restart", 88L)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.jobExecutionId", is(89)));
		}

		@Test
		@DisplayName("FAILED 가 아니면 409 COMMON_CONFLICT 를 반환한다")
		void returnsConflictWhenNotFailed() throws Exception {
			// given
			given(catalogImportJobService.restart(88L)).willThrow(new BusinessException(ErrorCode.COMMON_CONFLICT));

			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs/{jobExecutionId}/restart", 88L)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COMMON_CONFLICT")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/admin/catalog/import-jobs/{jobExecutionId}/restart", 88L)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(catalogImportJobService, never()).restart(88L);
		}
	}
}
