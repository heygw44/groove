package com.groove.member.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.dto.AdminMemberDetailResponse;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.dto.AdminMemberSummaryResponse;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.service.AdminMemberService;

@WebMvcTest(AdminMemberController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminMemberControllerTest {

	private static final String BASE_URL = "/api/v1/admin/members";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	AdminMemberService adminMemberService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/members")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 회원 목록을 반환한다")
		void returnsMembersForAdmin() throws Exception {
			// given
			AdminMemberSummaryResponse response = new AdminMemberSummaryResponse(2L, "groover@groove.com", "그루버",
					MemberRole.USER, MemberStatus.ACTIVE, 3L, new BigDecimal("90000"), LocalDateTime.now());
			given(adminMemberService.getList(any())).willReturn(PageResponse.of(List.of(response), 0, 20, 1));

			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.content[0].email", is("groover@groove.com")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL).header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminMemberService, never()).getList(any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/members/{id}")
	class GetDetail {

		@Test
		@DisplayName("관리자면 200 과 회원 상세를 반환한다")
		void returnsDetailForAdmin() throws Exception {
			// given
			AdminMemberDetailResponse response = new AdminMemberDetailResponse(2L, "groover@groove.com", "그루버",
					MemberRole.USER, MemberStatus.ACTIVE, LocalDateTime.now(), 3L, new BigDecimal("90000"), 1L,
					List.of());
			given(adminMemberService.getDetail(2L)).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/2").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is("groover@groove.com")));
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/members/{id}/status")
	class ChangeStatus {

		@Test
		@DisplayName("정상 요청이면 200 을 반환한다")
		void returnsOkForValidRequest() throws Exception {
			// given
			AdminMemberDetailResponse response = new AdminMemberDetailResponse(2L, "groover@groove.com", "그루버",
					MemberRole.USER, MemberStatus.SUSPENDED, LocalDateTime.now(), 3L, new BigDecimal("90000"), 1L,
					List.of());
			given(adminMemberService.changeStatus(anyLong(), anyLong(), any())).willReturn(response);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/2/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("SUSPENDED")));
		}

		@Test
		@DisplayName("status 가 WITHDRAWN 이면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestForWithdrawnStatus() throws Exception {
			// when & then
			mockMvc.perform(patch(BASE_URL + "/2/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.WITHDRAWN, null))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminMemberService, never()).changeStatus(any(), any(), any());
		}

		@Test
		@DisplayName("사유가 200자를 넘으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenReasonTooLong() throws Exception {
			// given
			String tooLongReason = "가".repeat(201);

			// when & then
			mockMvc.perform(patch(BASE_URL + "/2/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, tooLongReason))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminMemberService, never()).changeStatus(any(), any(), any());
		}

		@Test
		@DisplayName("status 가 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenStatusMissing() throws Exception {
			// when & then
			mockMvc.perform(patch(BASE_URL + "/2/status")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminMemberService, never()).changeStatus(any(), any(), any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환한다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(patch(BASE_URL + "/2/status")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminMemberService, never()).changeStatus(any(), any(), any());
		}
	}
}
