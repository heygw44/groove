package com.groove.admin.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.service.AdminStatsService;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.member.entity.MemberRole;

@WebMvcTest(AdminStatsController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class AdminStatsControllerTest {

	private static final String BASE_URL = "/api/v1/admin/stats";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminStatsService adminStatsService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	@Nested
	@DisplayName("GET /api/v1/admin/stats/daily-sales")
	class GetDailySales {

		@Test
		@DisplayName("관리자면 200 과 일별 매출 목록을 반환한다")
		void returnsDailySalesForAdmin() throws Exception {
			// given
			DailySalesResponse response = new DailySalesResponse(LocalDate.of(2026, 9, 5), 2,
					new BigDecimal("60000"), BigDecimal.ZERO);
			given(adminStatsService.getDailySales(any())).willReturn(List.of(response));

			// when & then
			mockMvc.perform(get(BASE_URL + "/daily-sales").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data[0].orderCount", is(2)));
		}

		@Test
		@DisplayName("from 이 올바른 날짜 형식이 아니면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenFromInvalid() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/daily-sales")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("from", "2026-13-01"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminStatsService, never()).getDailySales(any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/daily-sales").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminStatsService, never()).getDailySales(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/daily-sales"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminStatsService, never()).getDailySales(any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/stats/popular-products")
	class GetPopularProducts {

		@Test
		@DisplayName("관리자면 200 과 인기 상품 목록을 반환한다")
		void returnsPopularProductsForAdmin() throws Exception {
			// given
			PopularProductResponse response = new PopularProductResponse(1L, "그루브 앨범", "그루브 아티스트", 5,
					new BigDecimal("250000"), 3);
			given(adminStatsService.getPopularProducts(any())).willReturn(List.of(response));

			// when & then
			mockMvc.perform(get(BASE_URL + "/popular-products").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data[0].productTitle", is("그루브 앨범")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/popular-products").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminStatsService, never()).getPopularProducts(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/popular-products"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminStatsService, never()).getPopularProducts(any());
		}

		@Test
		@DisplayName("sort 값이 허용되지 않으면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenSortInvalid() throws Exception {
			// given
			given(adminStatsService.getPopularProducts(any())).willThrow(
					new BusinessException(ErrorCode.COMMON_INVALID_INPUT));

			// when & then
			mockMvc.perform(get(BASE_URL + "/popular-products")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("sort", "foo"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/stats/limited-drops")
	class GetLimitedDropStats {

		@Test
		@DisplayName("관리자면 200 과 한정반 현황 목록을 반환한다")
		void returnsLimitedDropStatsForAdmin() throws Exception {
			// given
			LimitedDropStatsResponse response = new LimitedDropStatsResponse(1L, "그루브 앨범", LimitedDropStatus.OPEN,
					10, 3, 30.0, LocalDateTime.of(2026, 9, 1, 10, 0), LocalDateTime.of(2026, 9, 2, 10, 0), null,
					null);
			given(adminStatsService.getLimitedDropStats()).willReturn(List.of(response));

			// when & then
			mockMvc.perform(get(BASE_URL + "/limited-drops").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data[0].sellRate", is(30.0)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/limited-drops").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminStatsService, never()).getLimitedDropStats();
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/limited-drops"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminStatsService, never()).getLimitedDropStats();
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/stats/summary")
	class GetSummary {

		@Test
		@DisplayName("관리자면 200 과 요약 카드를 반환한다")
		void returnsSummaryForAdmin() throws Exception {
			// given
			AdminStatsSummaryResponse response = new AdminStatsSummaryResponse(new BigDecimal("100000"), 2, 1, 3);
			given(adminStatsService.getSummary()).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/summary").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.pendingOrderCount", is(3)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/summary").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminStatsService, never()).getSummary();
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/summary"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminStatsService, never()).getSummary();
		}
	}

}
