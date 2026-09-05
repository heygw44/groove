package com.groove.auth.jwt;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.global.config.JwtProperties;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

@WebMvcTest(AuthProbeController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class})
@ActiveProfiles("test")
class JwtAuthenticationFilterTest {

	private static final String OTHER_SIGNING_KEY = "other-secret-key-for-jwt-signing-must-be-long-enough-0000";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	JwtProperties jwtProperties;

	@Nested
	@DisplayName("GET /api/v1/probe/me")
	class ProbeMe {

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/probe/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}

		@Test
		@DisplayName("만료된 토큰이면 401 AUTH_EXPIRED_TOKEN 을 반환한다")
		void returnsExpiredTokenWhenTokenExpired() throws Exception {
			// given
			JwtProvider expiredProvider = new JwtProvider(
					new JwtProperties(jwtProperties.secret(), Duration.ofMillis(-1000), Duration.ofDays(14)));
			String token = expiredProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_EXPIRED_TOKEN")));
		}

		@Test
		@DisplayName("위조된(다른 키로 서명된) 토큰이면 401 AUTH_INVALID_TOKEN 을 반환한다")
		void returnsInvalidTokenWhenTokenForged() throws Exception {
			// given
			JwtProvider otherProvider = new JwtProvider(
					new JwtProperties(OTHER_SIGNING_KEY, Duration.ofMinutes(30), Duration.ofDays(14)));
			String token = otherProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_INVALID_TOKEN")));
		}

		@Test
		@DisplayName("정상 USER 토큰이면 200 과 memberId 를 반환한다")
		void returnsMemberIdWithValidUserToken() throws Exception {
			// given
			String token = jwtProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data", is(1)));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/probe")
	class AdminProbe {

		@Test
		@DisplayName("USER 토큰으로 호출하면 403 AUTH_FORBIDDEN 을 반환한다")
		void returnsForbiddenWithUserToken() throws Exception {
			// given
			String token = jwtProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_FORBIDDEN")));
		}

		@Test
		@DisplayName("ADMIN 토큰으로 호출하면 200을 반환한다")
		void returnsOkWithAdminToken() throws Exception {
			// given
			String token = jwtProvider.createAccessToken(1L, MemberRole.ADMIN);

			// when & then
			mockMvc.perform(get("/api/v1/admin/probe").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)));
		}
	}
}
