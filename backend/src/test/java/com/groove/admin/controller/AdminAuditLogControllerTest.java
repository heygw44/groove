package com.groove.admin.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
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

import com.groove.admin.dto.AdminAuditLogResponse;
import com.groove.admin.entity.AdminAuditAction;
import com.groove.admin.entity.AdminAuditTargetType;
import com.groove.admin.service.AdminAuditLogQueryService;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(AdminAuditLogController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminAuditLogControllerTest {

	private static final String BASE_URL = "/api/v1/admin/audit-logs";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminAuditLogQueryService adminAuditLogQueryService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/audit-logs")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 감사 로그 목록을 반환한다")
		void returnsAuditLogsForAdmin() throws Exception {
			// given
			AdminAuditLogResponse response = new AdminAuditLogResponse(1L, 2L, "관리자", AdminAuditAction.STOCK_ADJUST,
					AdminAuditTargetType.PRODUCT, 10L, "IN:10->15", "203.0.113.7", LocalDateTime.now());
			given(adminAuditLogQueryService.getList(any()))
					.willReturn(PageResponse.of(List.of(response), 0, 20, 1));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.content[0].adminNickname", is("관리자")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminAuditLogQueryService, never()).getList(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminAuditLogQueryService, never()).getList(any());
		}

		@Test
		@DisplayName("from 이 to 보다 이후면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenFromAfterTo() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("from", "2026-09-05")
							.param("to", "2026-09-01"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminAuditLogQueryService, never()).getList(any());
		}
	}
}
