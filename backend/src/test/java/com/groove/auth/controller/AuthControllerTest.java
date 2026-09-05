package com.groove.auth.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.cookie.RefreshTokenCookieFactory;
import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.dto.SignupResponse;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.service.AuthService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.config.JwtProperties;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;
import com.groove.member.entity.MemberRole;

import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, RefreshTokenCookieFactory.class})
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	JwtProperties jwtProperties;

	@MockitoBean
	AuthService authService;

	@Nested
	@DisplayName("POST /api/v1/auth/signup")
	class Signup {

		@Test
		@DisplayName("유효한 요청이면 201 과 회원가입 응답을 반환한다")
		void returnsCreatedWithSignupResponse() throws Exception {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			SignupResponse response = new SignupResponse(1L, request.email(), request.nickname());
			given(authService.signup(any(SignupRequest.class))).willReturn(response);

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.id", is(1)))
					.andExpect(jsonPath("$.data.email", is(request.email())))
					.andExpect(jsonPath("$.data.nickname", is(request.nickname())));
		}

		@ParameterizedTest
		@DisplayName("필드 값이 유효하지 않으면 400 과 필드 에러를 반환한다")
		@CsvSource({
			"invalid-email, password1, 그루버, email",
			"groover@groove.com, short12, 그루버, password",
			"groover@groove.com, password12345678901234, 그루버, password",
			"groover@groove.com, password1, 그, nickname",
			"'', password1, 그루버, email"
		})
		void returnsBadRequestWhenFieldInvalid(String email, String password, String nickname,
				String expectedField) throws Exception {
			// given
			SignupRequest request = new SignupRequest(email, password, nickname);

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem(expectedField)));
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 409 와 MEMBER_EMAIL_DUPLICATE 를 반환한다")
		void returnsConflictWhenEmailDuplicated() throws Exception {
			// given
			SignupRequest request = new SignupRequest("groover@groove.com", "password1", "그루버");
			willThrow(new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATE))
					.given(authService).signup(any(SignupRequest.class));

			// when & then
			mockMvc.perform(post("/api/v1/auth/signup")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code", is("MEMBER_EMAIL_DUPLICATE")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/login")
	class Login {

		@Test
		@DisplayName("로그인에 성공하면 토큰 응답과 refresh 쿠키를 반환한다")
		void returnsTokenResponseWithRefreshCookie() throws Exception {
			// given
			LoginRequest request = new LoginRequest("groover@groove.com", "password1");
			AuthTokens tokens = new AuthTokens("access-token", "refresh", 1800L);
			given(authService.login(any(LoginRequest.class))).willReturn(tokens);

			// when & then
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.accessToken", is("access-token")))
					.andExpect(jsonPath("$.data.tokenType", is("Bearer")))
					.andExpect(jsonPath("$.data.expiresIn", is(1800)))
					.andExpect(cookie().value("refreshToken", "refresh"))
					.andExpect(cookie().httpOnly("refreshToken", true))
					.andExpect(cookie().secure("refreshToken", false))
					.andExpect(cookie().path("refreshToken", "/api/v1/auth"))
					.andExpect(cookie().sameSite("refreshToken", "Lax"))
					.andExpect(cookie().maxAge("refreshToken", (int) jwtProperties.refreshTokenExpiry().toSeconds()));
		}

		@Test
		@DisplayName("자격 증명이 올바르지 않으면 401 AUTH_INVALID_CREDENTIALS 를 반환한다")
		void returnsUnauthorizedWhenCredentialsInvalid() throws Exception {
			// given
			LoginRequest request = new LoginRequest("groover@groove.com", "wrong-password");
			willThrow(new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS))
					.given(authService).login(any(LoginRequest.class));

			// when & then
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_INVALID_CREDENTIALS")));
		}

		@Test
		@DisplayName("탈퇴한 회원이면 403 MEMBER_WITHDRAWN 을 반환한다")
		void returnsForbiddenWhenMemberWithdrawn() throws Exception {
			// given
			LoginRequest request = new LoginRequest("groover@groove.com", "password1");
			willThrow(new BusinessException(ErrorCode.MEMBER_WITHDRAWN))
					.given(authService).login(any(LoginRequest.class));

			// when & then
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("MEMBER_WITHDRAWN")));
		}

		@Test
		@DisplayName("필드 값이 비어있으면 400 과 필드 에러를 반환한다")
		void returnsBadRequestWhenFieldBlank() throws Exception {
			// given
			LoginRequest request = new LoginRequest("", "");

			// when & then
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(request)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.error.code", is("COMMON_VALIDATION_FAILED")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("email")))
					.andExpect(jsonPath("$.error.fieldErrors[*].field", hasItem("password")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/reissue")
	class Reissue {

		@Test
		@DisplayName("유효한 쿠키면 200 과 새 토큰, 새 refresh 쿠키를 반환한다")
		void returnsNewTokensWhenCookieValid() throws Exception {
			// given
			AuthTokens tokens = new AuthTokens("new-access", "new-refresh", 1800L);
			given(authService.reissue("old")).willReturn(tokens);

			// when & then
			mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refreshToken", "old")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.accessToken", is("new-access")))
					.andExpect(cookie().value("refreshToken", "new-refresh"));
		}

		@Test
		@DisplayName("쿠키가 없으면 401 AUTH_REFRESH_TOKEN_NOT_FOUND 를 반환한다")
		void returnsUnauthorizedWhenCookieMissing() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND))
					.given(authService).reissue(isNull());

			// when & then
			mockMvc.perform(post("/api/v1/auth/reissue"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_NOT_FOUND")));
			verify(authService).reissue(isNull());
		}

		@Test
		@DisplayName("저장된 토큰과 다르면 401 AUTH_REFRESH_TOKEN_MISMATCH 를 반환한다")
		void returnsUnauthorizedWhenTokenMismatch() throws Exception {
			// given
			willThrow(new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH))
					.given(authService).reissue(eq("stolen"));

			// when & then
			mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refreshToken", "stolen")))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_MISMATCH")));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/logout")
	class Logout {

		@Test
		@DisplayName("토큰 없이 호출하면 401 AUTH_UNAUTHORIZED 를 반환하고 서비스는 호출되지 않는다")
		void returnsUnauthorizedWithoutToken() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/auth/logout"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
			verify(authService, never()).logout(any());
		}

		@Test
		@DisplayName("인증된 요청이면 200 과 만료된 refresh 쿠키를 반환한다")
		void expiresCookieWhenAuthenticated() throws Exception {
			// given
			String accessToken = jwtProvider.createAccessToken(1L, MemberRole.USER);

			// when & then
			mockMvc.perform(post("/api/v1/auth/logout")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(cookie().maxAge("refreshToken", 0));
			verify(authService).logout(1L);
		}
	}
}
