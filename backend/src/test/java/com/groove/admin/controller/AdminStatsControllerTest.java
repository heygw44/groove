package com.groove.admin.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.admin.dto.AdminStatsSummaryResponse;
import com.groove.admin.dto.DailySalesResponse;
import com.groove.admin.dto.DailySalesStatsResponse;
import com.groove.admin.dto.LimitedDropStatsResponse;
import com.groove.admin.dto.PopularProductResponse;
import com.groove.admin.dto.PopularProductStatsResponse;
import com.groove.admin.service.AdminStatsService;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.member.entity.MemberRole;
import com.groove.stats.dto.ReconcileLogResponse;
import com.groove.stats.dto.SalesAggregationRequest;
import com.groove.stats.dto.SalesAggregationResponse;
import com.groove.stats.service.SalesAggregationAdminService;
import com.groove.stats.service.SalesReconcileLogQueryService;

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

	@Autowired
	ObjectMapper objectMapper;

	@MockitoBean
	AdminStatsService adminStatsService;

	@MockitoBean
	SalesAggregationAdminService salesAggregationAdminService;

	@MockitoBean
	SalesReconcileLogQueryService salesReconcileLogQueryService;

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
		@DisplayName("관리자면 200 과 일별 매출 목록·기준시각을 반환한다")
		void returnsDailySalesForAdmin() throws Exception {
			// given
			DailySalesResponse row = new DailySalesResponse(LocalDate.of(2026, 9, 5), 2,
					new BigDecimal("60000"), BigDecimal.ZERO);
			LocalDateTime aggregatedAt = LocalDateTime.of(2026, 9, 5, 10, 15);
			DailySalesStatsResponse response = DailySalesStatsResponse.of(List.of(row), aggregatedAt);
			given(adminStatsService.getDailySales(any())).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/daily-sales").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.items[0].orderCount", is(2)))
					.andExpect(jsonPath("$.data.aggregatedAt", is("2026-09-05T10:15:00")));
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
		@DisplayName("관리자면 200 과 인기 상품 목록·기준시각을 반환한다")
		void returnsPopularProductsForAdmin() throws Exception {
			// given
			PopularProductResponse row = new PopularProductResponse(1L, "그루브 앨범", "그루브 아티스트", 5,
					new BigDecimal("250000"), 3);
			LocalDateTime aggregatedAt = LocalDateTime.of(2026, 9, 5, 10, 15);
			PopularProductStatsResponse response = PopularProductStatsResponse.of(List.of(row), aggregatedAt);
			given(adminStatsService.getPopularProducts(any())).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/popular-products").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.items[0].productTitle", is("그루브 앨범")))
					.andExpect(jsonPath("$.data.aggregatedAt", is("2026-09-05T10:15:00")));
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
					null, null);
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

	@Nested
	@DisplayName("POST /api/v1/admin/stats/aggregations")
	class Aggregate {

		@Test
		@DisplayName("관리자면 200 과 처리한 날짜 수를 반환한다")
		void returnsAggregatedDaysForAdmin() throws Exception {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2026, 8, 1),
					LocalDate.of(2026, 8, 31));
			given(salesAggregationAdminService.aggregate(anyLong(), any())).willReturn(
					new SalesAggregationResponse(31));

			// when & then
			mockMvc.perform(post(BASE_URL + "/aggregations")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.aggregatedDays", is(31)));
		}

		@Test
		@DisplayName("이미 실행 중이면 409 STATS_AGGREGATION_RUNNING 을 반환한다")
		void returnsConflictWhenAlreadyRunning() throws Exception {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2026, 8, 1),
					LocalDate.of(2026, 8, 31));
			given(salesAggregationAdminService.aggregate(anyLong(), any())).willThrow(
					new BusinessException(ErrorCode.STATS_AGGREGATION_RUNNING));

			// when & then
			mockMvc.perform(post(BASE_URL + "/aggregations")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("STATS_AGGREGATION_RUNNING")));
		}

		@Test
		@DisplayName("필수값이 없으면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenMissingField() throws Exception {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(null, LocalDate.of(2026, 8, 31));

			// when & then
			mockMvc.perform(post(BASE_URL + "/aggregations")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(salesAggregationAdminService, never()).aggregate(anyLong(), any());
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2026, 8, 1),
					LocalDate.of(2026, 8, 31));

			// when & then
			mockMvc.perform(post(BASE_URL + "/aggregations")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(salesAggregationAdminService, never()).aggregate(anyLong(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// given
			SalesAggregationRequest request = new SalesAggregationRequest(LocalDate.of(2026, 8, 1),
					LocalDate.of(2026, 8, 31));

			// when & then
			mockMvc.perform(post(BASE_URL + "/aggregations")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(salesAggregationAdminService, never()).aggregate(anyLong(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/stats/reconcile-logs")
	class GetReconcileLogs {

		@Test
		@DisplayName("관리자면 200 과 대사 로그 목록을 반환한다")
		void returnsReconcileLogsForAdmin() throws Exception {
			// given
			ReconcileLogResponse row = new ReconcileLogResponse(1L, LocalDate.of(2031, 3, 15),
					"DAILY_ORDER_COUNT", "CRITICAL", new BigDecimal("1"), new BigDecimal("2"), false,
					LocalDateTime.of(2031, 3, 16, 5, 0));
			PageResponse<ReconcileLogResponse> response = PageResponse.of(List.of(row), 0, 20, 1);
			given(salesReconcileLogQueryService.getList(any())).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/reconcile-logs").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.content[0].metric", is("DAILY_ORDER_COUNT")))
					.andExpect(jsonPath("$.data.content[0].repaired", is(false)));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenForUser() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/reconcile-logs").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(salesReconcileLogQueryService, never()).getList(any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/reconcile-logs"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(salesReconcileLogQueryService, never()).getList(any());
		}
	}

}
