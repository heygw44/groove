package com.groove.coupon.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.groove.coupon.dto.AvailableCouponResponse;
import com.groove.coupon.dto.CouponIssueRequest;
import com.groove.coupon.dto.CouponIssueResponse;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.service.MemberCouponService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JacksonConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(CouponController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, JacksonConfig.class})
@ActiveProfiles("test")
class CouponControllerTest {

	private static final Long MEMBER_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	MemberCouponService memberCouponService;

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private CouponIssueResponse sampleIssueResponse() {
		return new CouponIssueResponse(10L, "GROOVE10", "10% 할인 쿠폰", DiscountType.RATE, BigDecimal.TEN,
				LocalDateTime.now().plusDays(7));
	}

	@Nested
	@DisplayName("POST /api/v1/coupons/issue")
	class Issue {

		@Test
		@DisplayName("로그인 회원이면 201 과 발급 결과를 반환한다")
		void issuesCouponWhenAuthenticated() throws Exception {
			// given
			given(memberCouponService.issue(eq(MEMBER_ID), any())).willReturn(sampleIssueResponse());

			// when & then
			mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CouponIssueRequest("GROOVE10"))))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.data.couponCode", is("GROOVE10")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/coupons/issue")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CouponIssueRequest("GROOVE10"))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(memberCouponService, never()).issue(any(), any());
		}

		@Test
		@DisplayName("코드가 비어 있으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenCodeBlank() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CouponIssueRequest(""))))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")));
			verify(memberCouponService, never()).issue(any(), any());
		}

		@Test
		@DisplayName("이미 발급받은 쿠폰이면 409 COUPON_ALREADY_ISSUED 를 반환한다")
		void returnsConflictWhenAlreadyIssued() throws Exception {
			// given
			given(memberCouponService.issue(eq(MEMBER_ID), any()))
					.willThrow(new BusinessException(ErrorCode.COUPON_ALREADY_ISSUED));

			// when & then
			mockMvc.perform(post("/api/v1/coupons/issue")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new CouponIssueRequest("GROOVE10"))))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("COUPON_ALREADY_ISSUED")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/coupons/available")
	class GetAvailable {

		@Test
		@DisplayName("로그인 회원이면 200 과 적용 가능 쿠폰 목록을 반환한다")
		void returnsAvailableCouponsWhenAuthenticated() throws Exception {
			// given
			AvailableCouponResponse response = new AvailableCouponResponse(10L, "GROOVE10", "10% 할인 쿠폰",
					DiscountType.RATE, BigDecimal.TEN, BigDecimal.ZERO, null, LocalDateTime.now().plusDays(7),
					BigDecimal.valueOf(1000));
			given(memberCouponService.getAvailableCoupons(eq(MEMBER_ID), any())).willReturn(List.of(response));

			// when & then
			mockMvc.perform(get("/api/v1/coupons/available")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.param("orderAmount", "10000"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].couponCode", is("GROOVE10")));
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/coupons/available").param("orderAmount", "10000"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(memberCouponService, never()).getAvailableCoupons(any(), any());
		}

		@Test
		@DisplayName("orderAmount 형식이 잘못되면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenOrderAmountMalformed() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/coupons/available")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.param("orderAmount", "not-a-number"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
			verify(memberCouponService, never()).getAvailableCoupons(any(), any());
		}

		@Test
		@DisplayName("orderAmount 를 보내지 않으면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenOrderAmountMissing() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/coupons/available")
							.header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
			verify(memberCouponService, never()).getAvailableCoupons(any(), any());
		}
	}
}
