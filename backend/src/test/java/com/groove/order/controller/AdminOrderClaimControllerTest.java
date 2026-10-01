package com.groove.order.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;
import com.groove.order.dto.AdminOrderClaimCountResponse;
import com.groove.order.dto.AdminOrderClaimCountResponse.ClaimCounts;
import com.groove.order.service.AdminOrderClaimService;

@WebMvcTest(AdminOrderClaimController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminOrderClaimControllerTest {

	private static final String BASE_URL = "/api/v1/admin/order-claims";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminOrderClaimService adminOrderClaimService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/order-claims/counts")
	class GetCounts {

		@Test
		@DisplayName("관리자면 200 과 유형별 상태 건수를 반환한다")
		void returnsCountsForAdmin() throws Exception {
			// given
			given(adminOrderClaimService.getCounts()).willReturn(new AdminOrderClaimCountResponse(
					new ClaimCounts(3, 0, 5, 1, 2, 11), new ClaimCounts(1, 4, 6, 0, 0, 11)));

			// when & then
			mockMvc.perform(get(BASE_URL + "/counts").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.cancel.requested", is(3)))
					.andExpect(jsonPath("$.data.cancel.collecting", is(0)))
					.andExpect(jsonPath("$.data.cancel.done", is(5)))
					.andExpect(jsonPath("$.data.cancel.rejected", is(1)))
					.andExpect(jsonPath("$.data.cancel.withdrawn", is(2)))
					.andExpect(jsonPath("$.data.cancel.total", is(11)))
					.andExpect(jsonPath("$.data.returns.collecting", is(4)))
					.andExpect(jsonPath("$.data.returns.total", is(11)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환한다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/counts").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminOrderClaimService, never()).getCounts();
		}
	}
}
