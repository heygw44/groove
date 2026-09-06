package com.groove.catalog.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
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

	@MockitoBean
	CatalogLookupService catalogLookupService;

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
}
