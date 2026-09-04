package com.groove.limited.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import com.groove.global.config.ClockConfig;
import com.groove.global.config.JwtProperties;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.limited.dto.LimitedDropDetailResponse;
import com.groove.limited.dto.LimitedDropListResponse;
import com.groove.limited.dto.LimitedDropSummaryResponse;
import com.groove.limited.entity.LimitedDropStatus;
import com.groove.limited.service.LimitedDropService;
import com.groove.member.entity.MemberRole;

@WebMvcTest(LimitedDropController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, ClockConfig.class})
@ActiveProfiles("test")
class LimitedDropControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtProvider jwtProvider;

	@Autowired
	private JwtProperties jwtProperties;

	@MockitoBean
	private LimitedDropService limitedDropService;

	private String bearer() {
		return "Bearer " + jwtProvider.createAccessToken(1L, MemberRole.USER);
	}

	private LimitedDropSummaryResponse sampleSummary() {
		LimitedDropSummaryResponse.ProductSummary product = new LimitedDropSummaryResponse.ProductSummary(
				1L, "Kind of Blue", "Miles Davis", new BigDecimal("45000.00"), "https://cdn.groove.com/0.jpg");
		return new LimitedDropSummaryResponse(1L, product, 100, 80, 2,
				OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(2), LimitedDropStatus.OPEN);
	}

	private LimitedDropDetailResponse sampleDetail(Boolean purchased) {
		LimitedDropSummaryResponse.ProductSummary product = new LimitedDropSummaryResponse.ProductSummary(
				1L, "Kind of Blue", "Miles Davis", new BigDecimal("45000.00"), "https://cdn.groove.com/0.jpg");
		return new LimitedDropDetailResponse(1L, product, 100, 80, 2,
				OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(2), LimitedDropStatus.OPEN,
				purchased, OffsetDateTime.now());
	}

	@Nested
	@DisplayName("GET /api/v1/limited-drops")
	class GetList {

		@Test
		@DisplayName("비로그인 상태로도 200 과 목록을 반환한다")
		void returnsListWithoutAuthentication() throws Exception {
			// given
			LimitedDropListResponse response = new LimitedDropListResponse(List.of(sampleSummary()),
					OffsetDateTime.now());
			given(limitedDropService.getList(null)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.drops[0].id", is(1)));
		}

		@Test
		@DisplayName("캐시되지 않도록 Cache-Control: no-store 를 내려준다")
		void returnsNoStoreCacheControl() throws Exception {
			// given
			LimitedDropListResponse response = new LimitedDropListResponse(List.of(), OffsetDateTime.now());
			given(limitedDropService.getList(null)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops"))
					.andExpect(status().isOk())
					.andExpect(header().string("Cache-Control", "no-store"));
		}

		@Test
		@DisplayName("serverTime 이 +09:00 오프셋으로 끝난다")
		void returnsServerTimeWithSeoulOffset() throws Exception {
			// given
			LimitedDropListResponse response = new LimitedDropListResponse(List.of(), OffsetDateTime.now());
			given(limitedDropService.getList(null)).willReturn(response);

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.serverTime", endsWith("+09:00")));
		}

		@Test
		@DisplayName("status 가 잘못된 값이면 400 COMMON_INVALID_INPUT 을 반환한다")
		void returnsBadRequestWhenStatusInvalid() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/limited-drops").param("status", "NOT_A_STATUS"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_INVALID_INPUT")));
		}

		@Test
		@DisplayName("GET 만 공개돼 있어 POST 는 인증이 없으면 401 을 반환한다")
		void returnsUnauthorizedForPostWithoutAuth() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/limited-drops"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/limited-drops/{id}")
	class GetDetail {

		@Test
		@DisplayName("비로그인 상태로도 200 과 상세를 반환하고 purchased 키가 없다")
		void returnsDetailWithoutAuthentication() throws Exception {
			// given
			given(limitedDropService.getDetail(eq(1L), isNull())).willReturn(sampleDetail(null));

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", 1L))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.id", is(1)))
					.andExpect(jsonPath("$.data.purchased").doesNotExist());
		}

		@Test
		@DisplayName("로그인 상태면 회원 id 를 서비스에 넘기고 purchased 를 반환한다")
		void passesMemberIdWhenAuthenticated() throws Exception {
			// given
			given(limitedDropService.getDetail(eq(1L), eq(1L))).willReturn(sampleDetail(true));

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", 1L).header(HttpHeaders.AUTHORIZATION, bearer()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.purchased", is(true)));
			verify(limitedDropService).getDetail(eq(1L), eq(1L));
		}

		@Test
		@DisplayName("만료된 토큰이라도 200 이고 비로그인으로 처리된다")
		void treatsExpiredTokenAsAnonymous() throws Exception {
			// given
			JwtProvider expiredProvider = new JwtProvider(
					new JwtProperties(jwtProperties.secret(), Duration.ofMillis(-1000), Duration.ofDays(14)));
			String expiredToken = expiredProvider.createAccessToken(1L, MemberRole.USER);
			given(limitedDropService.getDetail(eq(1L), isNull())).willReturn(sampleDetail(null));

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", 1L)
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
					.andExpect(status().isOk());
			verify(limitedDropService).getDetail(eq(1L), isNull());
		}

		@Test
		@DisplayName("캐시되지 않도록 Cache-Control: no-store 를 내려준다")
		void returnsNoStoreCacheControl() throws Exception {
			// given
			given(limitedDropService.getDetail(eq(1L), isNull())).willReturn(sampleDetail(null));

			// when & then
			mockMvc.perform(get("/api/v1/limited-drops/{id}", 1L))
					.andExpect(status().isOk())
					.andExpect(header().string("Cache-Control", "no-store"));
		}
	}
}
