package com.groove.coupon.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.auth.jwt.JwtProvider;
import com.groove.coupon.dto.MemberCouponResponse;
import com.groove.coupon.dto.MemberCouponStatus;
import com.groove.coupon.entity.DiscountType;
import com.groove.coupon.service.MemberCouponService;
import com.groove.global.config.JacksonConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(MemberCouponController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, JacksonConfig.class})
@ActiveProfiles("test")
class MemberCouponControllerTest {

	private static final Long MEMBER_ID = 1L;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@MockitoBean
	MemberCouponService memberCouponService;

	private String userToken() {
		return "Bearer " + jwtProvider.createAccessToken(MEMBER_ID, MemberRole.USER);
	}

	private MemberCouponResponse sampleResponse() {
		return new MemberCouponResponse(10L, 1L, "GROOVE10", "10% 할인 쿠폰", DiscountType.RATE, BigDecimal.TEN,
				BigDecimal.ZERO, null, LocalDateTime.now().plusDays(7), false, false, LocalDateTime.now(), null);
	}

	@Nested
	@DisplayName("GET /api/v1/members/me/coupons")
	class GetMyCoupons {

		@Test
		@DisplayName("로그인 회원이면 200 과 쿠폰 목록을 반환한다")
		void returnsCouponsWhenAuthenticated() throws Exception {
			// given
			given(memberCouponService.getMyCoupons(eq(MEMBER_ID), eq(null))).willReturn(List.of(sampleResponse()));

			// when & then
			mockMvc.perform(get("/api/v1/members/me/coupons").header(HttpHeaders.AUTHORIZATION, userToken()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data[0].couponCode", is("GROOVE10")));
		}

		@Test
		@DisplayName("status=usable 이면 필터를 그대로 전달한다")
		void passesStatusFilter() throws Exception {
			// given
			given(memberCouponService.getMyCoupons(eq(MEMBER_ID), eq(MemberCouponStatus.USABLE)))
					.willReturn(List.of(sampleResponse()));

			// when & then
			mockMvc.perform(get("/api/v1/members/me/coupons")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.param("status", "usable"))
					.andExpect(status().isOk());
			verify(memberCouponService).getMyCoupons(MEMBER_ID, MemberCouponStatus.USABLE);
		}

		@Test
		@DisplayName("잘못된 status 값이면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenStatusInvalid() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me/coupons")
							.header(HttpHeaders.AUTHORIZATION, userToken())
							.param("status", "unknown"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
			verify(memberCouponService, never()).getMyCoupons(any(), any());
		}

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me/coupons"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(memberCouponService, never()).getMyCoupons(any(), any());
		}
	}
}
