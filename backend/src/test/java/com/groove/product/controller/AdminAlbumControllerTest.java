package com.groove.product.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.product.dto.AdminAlbumSummaryResponse;
import com.groove.product.service.AlbumService;

@WebMvcTest(AdminAlbumController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminAlbumControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AlbumService albumService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/albums")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 페이지 응답을 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			AdminAlbumSummaryResponse summary = new AdminAlbumSummaryResponse(1L, "Kind of Blue", "Miles Davis", 1959);
			PageResponse<AdminAlbumSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
			given(albumService.getAdminList(any(), any())).willReturn(pageResponse);

			// when & then
			mockMvc.perform(get("/api/v1/admin/albums").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(1)))
					.andExpect(jsonPath("$.data.content[0].title", is("Kind of Blue")))
					.andExpect(jsonPath("$.data.content[0].artistName", is("Miles Davis")))
					.andExpect(jsonPath("$.data.content[0].originalReleaseYear", is(1959)));
		}

		@Test
		@DisplayName("keyword 쿼리 파라미터를 서비스에 그대로 전달한다")
		void passesKeywordToService() throws Exception {
			// given
			PageResponse<AdminAlbumSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
			given(albumService.getAdminList(any(), any())).willReturn(pageResponse);

			// when & then
			mockMvc.perform(get("/api/v1/admin/albums").param("keyword", "Blue")
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk());
			verify(albumService).getAdminList(eq("Blue"), any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/albums").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(albumService, never()).getAdminList(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/albums"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(albumService, never()).getAdminList(any(), any());
		}
	}
}
