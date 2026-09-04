package com.groove.coupon.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openapitools.jackson.nullable.JsonNullable;
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
import com.groove.coupon.dto.AdminCouponResponse;
import com.groove.coupon.dto.AdminCouponSummaryResponse;
import com.groove.coupon.dto.CouponCreateRequest;
import com.groove.coupon.dto.CouponUpdateRequest;
import com.groove.coupon.entity.CouponStatus;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.service.AdminCouponService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.global.config.JacksonConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(AdminCouponController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, JacksonConfig.class})
@ActiveProfiles("test")
class AdminCouponControllerTest {

	private static final Long COUPON_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	AdminCouponService adminCouponService;

	private String adminToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.ADMIN);
	}

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private CouponCreateRequest sampleCreateRequest() {
		return new CouponCreateRequest("WELCOME1000", "웰컴 쿠폰", DiscountType.FIXED, BigDecimal.valueOf(1000), null,
				null, null, LocalDateTime.now().plusDays(7));
	}

	private CouponUpdateRequest emptyUpdateRequest() {
		return new CouponUpdateRequest(null, null, null, null, JsonNullable.undefined(), JsonNullable.undefined(),
				null, null);
	}

	private AdminCouponResponse sampleResponse() {
		return new AdminCouponResponse(COUPON_ID, "WELCOME1000", "웰컴 쿠폰", DiscountType.FIXED,
				BigDecimal.valueOf(1000), BigDecimal.ZERO, null, null, 0, LocalDateTime.now().plusDays(7),
				CouponStatus.ACTIVE, null, null);
	}

	@Nested
	@DisplayName("POST /api/v1/admin/coupons")
	class Create {

		@Test
		@DisplayName("관리자면 201 과 등록된 쿠폰 정보를 반환한다")
		void createsCouponWhenAdmin() throws Exception {
			// given
			given(adminCouponService.create(eq(1L), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.code", is("WELCOME1000")));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminCouponService, never()).create(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(sampleCreateRequest())))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminCouponService, never()).create(any(), any());
		}

		@Test
		@DisplayName("코드가 소문자 5자면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenCodeInvalid() throws Exception {
			// given
			CouponCreateRequest request = new CouponCreateRequest("abcde", "웰컴 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, null, LocalDateTime.now().plusDays(7));

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("code")));
		}

		@Test
		@DisplayName("RATE 할인 값이 150 이면 400 을 반환한다")
		void returnsBadRequestWhenRateExceeds100() throws Exception {
			// given
			CouponCreateRequest request = new CouponCreateRequest("RATE150COUPON", "정률 쿠폰", DiscountType.RATE,
					BigDecimal.valueOf(150), null, null, null, LocalDateTime.now().plusDays(7));

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}

		@Test
		@DisplayName("만료일이 과거면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenExpiresAtInPast() throws Exception {
			// given
			CouponCreateRequest request = new CouponCreateRequest("PASTEXPIRED1", "만료 쿠폰", DiscountType.FIXED,
					BigDecimal.valueOf(1000), null, null, null, LocalDateTime.now().minusDays(1));

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("expiresAt")));
		}

		@Test
		@DisplayName("할인 값이 0이면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenDiscountValueZero() throws Exception {
			// given
			CouponCreateRequest request = new CouponCreateRequest("ZEROVALUE1", "0원 쿠폰", DiscountType.FIXED,
					BigDecimal.ZERO, null, null, null, LocalDateTime.now().plusDays(7));

			// when & then
			mockMvc.perform(post("/api/v1/admin/coupons")
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("discountValue")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/coupons")
	class GetList {

		@Test
		@DisplayName("관리자면 200 과 페이지 응답을 반환한다")
		void returnsPageWhenAdmin() throws Exception {
			// given
			AdminCouponSummaryResponse summary = new AdminCouponSummaryResponse(COUPON_ID, "WELCOME1000", "웰컴 쿠폰",
					DiscountType.FIXED, BigDecimal.valueOf(1000), BigDecimal.ZERO, null, null, 0, 0L,
					LocalDateTime.now().plusDays(7), CouponStatus.ACTIVE, null);
			PageResponse<AdminCouponSummaryResponse> pageResponse = PageResponse.from(
					new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
			given(adminCouponService.getList(any(), any())).willReturn(pageResponse);

			// when & then
			mockMvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.content[0].id", is(COUPON_ID.intValue())));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/coupons").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminCouponService, never()).getList(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/admin/coupons"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminCouponService, never()).getList(any(), any());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/admin/coupons/{id}")
	class Update {

		@Test
		@DisplayName("관리자면 200 과 수정된 쿠폰 정보를 반환한다")
		void updatesCouponWhenAdmin() throws Exception {
			// given
			given(adminCouponService.update(eq(1L), eq(COUPON_ID), any())).willReturn(sampleResponse());

			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(COUPON_ID.intValue())));
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminCouponService, never()).update(any(), any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminCouponService, never()).update(any(), any(), any());
		}

		@Test
		@DisplayName("존재하지 않는 쿠폰이면 404 COUPON_NOT_FOUND 를 반환한다")
		void returnsNotFoundWhenCouponMissing() throws Exception {
			// given
			given(adminCouponService.update(eq(1L), eq(COUPON_ID), any()))
					.willThrow(new BusinessException(ErrorCode.COUPON_NOT_FOUND));

			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(emptyUpdateRequest())))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code", is("COUPON_NOT_FOUND")));
		}

		@Test
		@DisplayName("RATE 할인 값이 150 이면 400 을 반환한다")
		void returnsBadRequestWhenRateExceeds100() throws Exception {
			// given
			CouponUpdateRequest request = new CouponUpdateRequest(null, DiscountType.RATE, BigDecimal.valueOf(150),
					null, JsonNullable.undefined(), JsonNullable.undefined(), null, null);

			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}

		@Test
		@DisplayName("totalQuantity 가 명시적 null 이면 해제 요청으로 서비스에 전달된다")
		void bindsTotalQuantityAsExplicitNullWhenBodyHasNullValue() throws Exception {
			// given
			given(adminCouponService.update(eq(1L), eq(COUPON_ID), any())).willReturn(sampleResponse());
			ArgumentCaptor<CouponUpdateRequest> captor = ArgumentCaptor.forClass(CouponUpdateRequest.class);

			// when
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"totalQuantity\":null}"))
					.andExpect(status().isOk());

			// then
			verify(adminCouponService).update(eq(1L), eq(COUPON_ID), captor.capture());
			assertThat(captor.getValue().totalQuantity()).isEqualTo(JsonNullable.of(null));
		}

		@Test
		@DisplayName("totalQuantity 키가 없으면 유지 요청으로 바인딩된다")
		void bindsTotalQuantityAsUndefinedWhenBodyOmitsKey() throws Exception {
			// given
			given(adminCouponService.update(eq(1L), eq(COUPON_ID), any())).willReturn(sampleResponse());
			ArgumentCaptor<CouponUpdateRequest> captor = ArgumentCaptor.forClass(CouponUpdateRequest.class);

			// when
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"name\":\"x\"}"))
					.andExpect(status().isOk());

			// then
			verify(adminCouponService).update(eq(1L), eq(COUPON_ID), captor.capture());
			assertThat(captor.getValue().totalQuantity()).isEqualTo(JsonNullable.undefined());
		}

		@Test
		@DisplayName("totalQuantity 가 0이면 400 COMMON_VALIDATION_FAILED 를 반환한다")
		void returnsBadRequestWhenTotalQuantityZero() throws Exception {
			// when & then
			mockMvc.perform(patch("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"totalQuantity\":0}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/admin/coupons/{id}")
	class Disable {

		@Test
		@DisplayName("관리자면 200 을 반환하고 쿠폰을 비활성화한다")
		void disablesCouponWhenAdmin() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, adminToken()))
					.andExpect(status().isOk());
			verify(adminCouponService).disable(1L, COUPON_ID);
		}

		@Test
		@DisplayName("일반 회원이면 403 AUTH_FORBIDDEN 을 반환하고 서비스는 호출되지 않는다")
		void returnsForbiddenWhenNotAdmin() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/coupons/{id}", COUPON_ID)
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
			verify(adminCouponService, never()).disable(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(delete("/api/v1/admin/coupons/{id}", COUPON_ID))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(adminCouponService, never()).disable(any(), any());
		}
	}
}
