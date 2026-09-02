package com.groove.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.global.config.JwtProperties;
import com.groove.member.entity.MemberRole;
import com.groove.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtProperties jwtProperties;

	@Nested
	@DisplayName("로그인 → 재발급 → 로그아웃 흐름")
	class LoginReissueLogoutFlow {

		@Test
		@DisplayName("refresh token 을 회전하고 로그아웃 후 재사용을 거부한다")
		void rotatesRefreshTokenAndRejectsReuseAfterLogout() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// when
			MvcResult loginResult = login(email, password);
			Cookie firstRefreshCookie = loginResult.getResponse().getCookie("refreshToken");

			// then
			assertThat(firstRefreshCookie).isNotNull();
			assertThat(refreshTokenRepository.findByMemberId(memberId)).contains(firstRefreshCookie.getValue());

			// 토큰 없이 로그아웃하면 401
			mockMvc.perform(post("/api/v1/auth/logout"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));

			// 쿠키로 재발급하면 새 refresh token 으로 회전한다
			MvcResult reissueResult = mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefreshCookie))
					.andExpect(status().isOk())
					.andReturn();
			Cookie secondRefreshCookie = reissueResult.getResponse().getCookie("refreshToken");
			assertThat(secondRefreshCookie).isNotNull();
			assertThat(secondRefreshCookie.getValue()).isNotEqualTo(firstRefreshCookie.getValue());
			assertThat(refreshTokenRepository.findByMemberId(memberId)).contains(secondRefreshCookie.getValue());

			// 이미 회전된(옛) 쿠키로 재발급을 시도하면 탈취로 간주해 세션을 폐기한다
			mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefreshCookie))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_MISMATCH")));
			assertThat(refreshTokenRepository.findByMemberId(memberId)).isEmpty();

			// 다시 로그인한 뒤 로그아웃하면 refresh token 이 삭제되고 쿠키가 만료된다
			MvcResult secondLoginResult = login(email, password);
			Cookie latestRefreshCookie = secondLoginResult.getResponse().getCookie("refreshToken");
			String latestAccessToken = objectMapper.readTree(secondLoginResult.getResponse().getContentAsString())
					.path("data").path("accessToken").asText();

			mockMvc.perform(post("/api/v1/auth/logout")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + latestAccessToken))
					.andExpect(status().isOk())
					.andExpect(cookie().maxAge("refreshToken", 0));
			assertThat(refreshTokenRepository.findByMemberId(memberId)).isEmpty();

			mockMvc.perform(post("/api/v1/auth/reissue").cookie(latestRefreshCookie))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_NOT_FOUND")));
		}

		@Test
		@DisplayName("비밀번호가 일치하지 않으면 로그인을 거부한다")
		void rejectsLoginWithWrongPassword() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			signup(email, "password1");

			// when & then
			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, "wrong-password"))))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_INVALID_CREDENTIALS")));
		}
	}

	@Nested
	@DisplayName("내 정보 조회 → 탈퇴 흐름")
	class GetMyInfoAndWithdrawFlow {

		@Test
		@DisplayName("탈퇴 후에는 내 정보 접근과 로그인을 거부한다")
		void rejectsAccessAndLoginAfterWithdraw() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			MvcResult loginResult = login(email, password);
			String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
					.path("data").path("accessToken").asText();

			// when & then
			mockMvc.perform(get("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is(email)))
					.andExpect(jsonPath("$.data.status", is("ACTIVE")));

			mockMvc.perform(delete("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk());
			assertThat(refreshTokenRepository.findByMemberId(memberId)).isEmpty();

			mockMvc.perform(get("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("MEMBER_WITHDRAWN")));

			mockMvc.perform(post("/api/v1/auth/login")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("MEMBER_WITHDRAWN")));
		}
	}

	@Nested
	@DisplayName("회원가입 → 로그인 → 보호 API → 재발급 → 로그아웃 전체 흐름")
	class FullAuthenticationLifecycle {

		@Test
		@DisplayName("전체 인증 생명주기를 정상적으로 완료한다")
		void completesFullAuthenticationLifecycle() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();

			// when
			MvcResult loginResult = login(email, password);
			String firstAccessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
					.path("data").path("accessToken").asText();
			Cookie firstRefreshCookie = loginResult.getResponse().getCookie("refreshToken");

			// then
			mockMvc.perform(get("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAccessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is(email)));

			MvcResult reissueResult = mockMvc.perform(post("/api/v1/auth/reissue").cookie(firstRefreshCookie))
					.andExpect(status().isOk())
					.andReturn();
			String secondAccessToken = objectMapper.readTree(reissueResult.getResponse().getContentAsString())
					.path("data").path("accessToken").asText();
			Cookie secondRefreshCookie = reissueResult.getResponse().getCookie("refreshToken");
			// access token 은 jti 없이 초 단위 iat/exp 로만 서명되어, 로그인과 재발급이 같은 초에 일어나면 값이 같을 수 있다.
			assertThat(secondRefreshCookie).isNotNull();
			assertThat(secondRefreshCookie.getValue()).isNotEqualTo(firstRefreshCookie.getValue());
			assertThat(refreshTokenRepository.findByMemberId(memberId)).contains(secondRefreshCookie.getValue());

			mockMvc.perform(get("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is(email)));

			mockMvc.perform(post("/api/v1/auth/logout")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + secondAccessToken))
					.andExpect(status().isOk())
					.andExpect(cookie().maxAge("refreshToken", 0));
			assertThat(refreshTokenRepository.findByMemberId(memberId)).isEmpty();

			mockMvc.perform(post("/api/v1/auth/reissue").cookie(secondRefreshCookie))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_NOT_FOUND")));
		}
	}

	@Nested
	@DisplayName("만료된 토큰 시나리오")
	class ExpiredTokenScenarios {

		@Test
		@DisplayName("만료된 access token 으로 보호 API 를 호출하면 401 을 반환한다")
		void rejectsExpiredAccessTokenOnProtectedApi() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			login(email, password);
			JwtProvider expiredProvider = new JwtProvider(
					new JwtProperties(jwtProperties.secret(), Duration.ofMillis(-1000), Duration.ofMillis(-1000)));
			String expiredAccessToken = expiredProvider.createAccessToken(memberId, MemberRole.USER);

			// when & then
			mockMvc.perform(get("/api/v1/members/me")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_EXPIRED_TOKEN")));
		}

		@Test
		@DisplayName("만료된 refresh token 으로 재발급을 요청하면 401 을 반환하고 기존 세션은 유지한다")
		void rejectsExpiredRefreshTokenOnReissue() throws Exception {
			// given
			String email = "flow-" + UUID.randomUUID() + "@groove.com";
			String password = "password1";
			MvcResult signupResult = signup(email, password);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			login(email, password);
			JwtProvider expiredProvider = new JwtProvider(
					new JwtProperties(jwtProperties.secret(), Duration.ofMillis(-1000), Duration.ofMillis(-1000)));
			String expiredRefreshToken = expiredProvider.createRefreshToken(memberId);

			// when & then
			mockMvc.perform(post("/api/v1/auth/reissue").cookie(new Cookie("refreshToken", expiredRefreshToken)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_EXPIRED_TOKEN")));
			assertThat(refreshTokenRepository.findByMemberId(memberId)).isPresent();
		}
	}

	private MvcResult signup(String email, String password) throws Exception {
		SignupRequest request = new SignupRequest(email, password, "그루버");
		return mockMvc.perform(post("/api/v1/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
	}

	private MvcResult login(String email, String password) throws Exception {
		LoginRequest request = new LoginRequest(email, password);
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andReturn();
	}
}
