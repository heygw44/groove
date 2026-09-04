package com.groove.limited.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.jwt.JwtProvider;
import com.groove.global.common.PageResponse;
import com.groove.global.config.JacksonConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.limited.dto.AdminLimitedDropDetailResponse;
import com.groove.limited.dto.AdminLimitedDropResponse;
import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.AdminLimitedPurchaseResponse;
import com.groove.limited.dto.LimitedDropCreateRequest;
import com.groove.limited.dto.LimitedDropUpdateRequest;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.AdminLimitedDropService;
import com.groove.member.entity.MemberRole;
import com.groove.order.entity.OrderStatus;

@WebMvcTest(AdminLimitedDropController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, JacksonConfig.class})
@ActiveProfiles("test")
class AdminLimitedDropControllerTest {

	private static final Long DROP_ID = 1L;
	private static final Long PRODUCT_ID = 100L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminLimitedDropService adminLimitedDropService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private LimitedDropCreateRequest sampleCreateRequest() {
		return new LimitedDropCreateRequest(PRODUCT_ID, 100, 2, LocalDateTime.now().plusDays(1),
				LocalDateTime.now().plusDays(2));
	}

	private LimitedDropUpdateRequest emptyUpdateRequest() {
		return new LimitedDropUpdateRequest(null, null, null, null);
	}

	private AdminLimitedDropResponse sampleResponse(LimitedDropStatus status) {
		LocalDateTime openAt = LocalDateTime.now().plusDays(1);
		LocalDateTime closeAt = LocalDateTime.now().plusDays(2);
		LocalDateTime now = LocalDateTime.now();
		return new AdminLimitedDropResponse(DROP_ID, PRODUCT_ID, "Kind of Blue", 100, 0, 100, 2, openAt, closeAt,
				status, now, now);
	}

	@Nested
	@DisplayName("POST /api/v1/admin/limited-drops")
	class Create {

		@Test
		@DisplayName("관리자면 201과 등록된 드롭 정보를 반환한다")
		void createsDropWhenAdmin() throws Exception {
			// given
			given(adminLimitedDropService.create(eq(1L), any()))
					.willReturn(sampleResponse(LimitedDropStatus.SCHEDULED));

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.status", is("SCHEDULED")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).create(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminLimitedDropService, never()).create(any(), any());
		}

		@Test
		@DisplayName("회원당 구매 제한이 6이면 400과 필드 에러를 반환한다")
		void returnsBadRequestWhenPerMemberLimitExceedsMax() throws Exception {
			// given
			LimitedDropCreateRequest request = new LimitedDropCreateRequest(PRODUCT_ID, 100, 6,
					LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("perMemberLimit")));
		}

		@Test
		@DisplayName("마감 시각이 오픈 시각보다 이전이면 400과 필드 에러를 반환한다")
		void returnsBadRequestWhenCloseAtBeforeOpenAt() throws Exception {
			// given
			LimitedDropCreateRequest request = new LimitedDropCreateRequest(PRODUCT_ID, 100, 2,
					LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(1));

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("closeAfterOpen")));
		}

		@Test
		@DisplayName("오픈 시각이 과거면 400과 필드 에러를 반환한다")
		void returnsBadRequestWhenOpenAtInPast() throws Exception {
			// given
			LimitedDropCreateRequest request = new LimitedDropCreateRequest(PRODUCT_ID, 100, 2,
					LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

			// when & then
			mockMvc.perform(post("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("openAt")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/limited-drops")
	class GetList {

		@Test
		@DisplayName("관리자면 200과 상태·페이지 조건이 반영된 페이지 응답을 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			AdminLimitedDropSummaryResponse summary = new AdminLimitedDropSummaryResponse(DROP_ID, PRODUCT_ID,
					"Kind of Blue", 100, 0, 2, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2),
					LimitedDropStatus.OPEN, LocalDateTime.now());
			PageResponse<AdminLimitedDropSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(summary)));
			given(adminLimitedDropService.getList(any(), any())).willReturn(pageResponse);
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

			// when
			mockMvc.perform(get("/api/v1/admin/limited-drops")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.param("status", "OPEN")
							.param("page", "0")
							.param("size", "5"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(DROP_ID.intValue())));

			// then
			verify(adminLimitedDropService).getList(eq(LimitedDropStatus.OPEN), pageableCaptor.capture());
			assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/limited-drops").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).getList(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/limited-drops"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminLimitedDropService, never()).getList(any(), any());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/limited-drops/{id}")
	class GetDetail {

		@Test
		@DisplayName("관리자면 200과 재고 대조·구매자 목록을 포함한 상세 정보를 반환한다")
		void returnsDetailWhenAdmin() throws Exception {
			// given
			AdminLimitedPurchaseResponse purchase = new AdminLimitedPurchaseResponse(1L, 5L, "구매자1", 20L,
					"20260904-TESTAB12", OrderStatus.PAID, 1, LocalDateTime.now());
			AdminLimitedDropDetailResponse detail = new AdminLimitedDropDetailResponse(DROP_ID, PRODUCT_ID,
					"Kind of Blue", 100, 10, 90, 88, 2, LocalDateTime.now().plusDays(1),
					LocalDateTime.now().plusDays(2), LimitedDropStatus.OPEN, LocalDateTime.now(), LocalDateTime.now(),
					List.of(purchase));
			given(adminLimitedDropService.getDetail(DROP_ID)).willReturn(detail);

			// when & then
			mockMvc.perform(get("/api/v1/admin/limited-drops/{id}", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.redisRemaining", is(88)))
					.andExpect(jsonPath("$.data.purchases[0].memberNickname", is("구매자1")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/limited-drops/{id}", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).getDetail(any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/limited-drops/{id}")
	class Update {

		@Test
		@DisplayName("관리자면 200과 수정된 드롭 정보를 반환한다")
		void updatesDropWhenAdmin() throws Exception {
			// given
			given(adminLimitedDropService.update(eq(1L), eq(DROP_ID), any()))
					.willReturn(sampleResponse(LimitedDropStatus.SCHEDULED));

			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("SCHEDULED")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).update(any(), any(), any());
		}

		@Test
		@DisplayName("총 수량이 0이면 400 COMMON_VALIDATION_FAILED를 반환한다")
		void returnsBadRequestWhenTotalQuantityZero() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"totalQuantity\":0}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(adminLimitedDropService, never()).update(any(), any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/limited-drops/{id}/open")
	class Open {

		@Test
		@DisplayName("관리자면 200과 OPEN 상태로 바뀐 드롭 정보를 반환한다")
		void opensDropWhenAdmin() throws Exception {
			// given
			given(adminLimitedDropService.open(1L, DROP_ID)).willReturn(sampleResponse(LimitedDropStatus.OPEN));

			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("OPEN")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/open", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).open(any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/limited-drops/{id}/close")
	class Close {

		@Test
		@DisplayName("관리자면 200과 CLOSED 상태로 바뀐 드롭 정보를 반환한다")
		void closesDropWhenAdmin() throws Exception {
			// given
			given(adminLimitedDropService.close(1L, DROP_ID)).willReturn(sampleResponse(LimitedDropStatus.CLOSED));

			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/close", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.status", is("CLOSED")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/limited-drops/{id}/close", DROP_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminLimitedDropService, never()).close(any(), any());
		}
	}
}
