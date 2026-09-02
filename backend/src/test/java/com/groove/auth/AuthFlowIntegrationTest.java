package com.groove.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.groove.auth.repository.RefreshTokenRepository;
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
