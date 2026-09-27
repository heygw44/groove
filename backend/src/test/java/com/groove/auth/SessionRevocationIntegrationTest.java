package com.groove.auth;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.groove.auth.jwt.JwtProvider;
import com.groove.fixture.MemberFixture;
import com.groove.member.dto.AdminMemberStatusChangeRequest;
import com.groove.member.dto.PasswordChangeRequest;
import com.groove.member.entity.Member;
import com.groove.member.entity.MemberRole;
import com.groove.member.entity.MemberStatus;
import com.groove.member.repository.MemberRepository;
import com.groove.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * 비밀번호 변경·탈퇴·관리자 정지가 기존 access token 을 즉시 무효화하는지 검증한다.
 * JWT 의 iat 는 초 단위이고 같은 초에 발급된 토큰은 통과시키므로, 로그인과 폐기가 같은 초에 겹치지 않도록
 * 폐기 전에 다음 초까지 대기한다.
 */
@AutoConfigureMockMvc
class SessionRevocationIntegrationTest extends IntegrationTestSupport {

	private static final String PASSWORD = "password1";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	MemberRepository memberRepository;

	@Nested
	@DisplayName("비밀번호 변경")
	class PasswordChange {

		@Test
		@DisplayName("변경 후에는 기존 access token·refresh token 이 모두 무효화되고 새 비밀번호로만 로그인할 수 있다")
		void revokesExistingSessionAfterPasswordChange() throws Exception {
			// given
			String email = "session-revoke-pw-" + UUID.randomUUID() + "@groove.com";
			signup(email, PASSWORD);
			MvcResult loginResult = login(email, PASSWORD);
			String accessToken = accessTokenOf(loginResult);
			Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
			sleepUntilNextSecond();

			// when
			String newPassword = "new-password1";
			mockMvc.perform(patch("/api/v1/members/me/password")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(new PasswordChangeRequest(PASSWORD, newPassword))))
					.andExpect(status().isOk());

			// then
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_TOKEN_REVOKED")));

			mockMvc.perform(post("/api/v1/auth/reissue").cookie(refreshCookie))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_REFRESH_TOKEN_NOT_FOUND")));

			MvcResult reloginResult = login(email, newPassword);
			String newAccessToken = accessTokenOf(reloginResult);
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.data.email", is(email)));
		}
	}

	@Nested
	@DisplayName("탈퇴")
	class Withdraw {

		@Test
		@DisplayName("탈퇴 후에는 기존 access token 이 무효화되고 재발급은 상태 검사에서 먼저 막힌다")
		void revokesExistingSessionAfterWithdraw() throws Exception {
			// given
			String email = "session-revoke-withdraw-" + UUID.randomUUID() + "@groove.com";
			signup(email, PASSWORD);
			MvcResult loginResult = login(email, PASSWORD);
			String accessToken = accessTokenOf(loginResult);
			Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
			sleepUntilNextSecond();

			// when
			mockMvc.perform(delete("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isOk());

			// then
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_TOKEN_REVOKED")));

			// reissue 는 세션 회전보다 회원 상태를 먼저 봐서 탈퇴 사유를 그대로 드러낸다.
			mockMvc.perform(post("/api/v1/auth/reissue").cookie(refreshCookie))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("MEMBER_WITHDRAWN")));
		}
	}

	@Nested
	@DisplayName("관리자 정지")
	class AdminSuspend {

		@Test
		@DisplayName("정지 후에는 기존 access token 이 무효화되고 재발급은 상태 검사에서 먼저 막힌다")
		void revokesExistingSessionAfterAdminSuspend() throws Exception {
			// given
			String email = "session-revoke-suspend-" + UUID.randomUUID() + "@groove.com";
			MvcResult signupResult = signup(email, PASSWORD);
			Long memberId = objectMapper.readTree(signupResult.getResponse().getContentAsString())
					.path("data").path("id").asLong();
			MvcResult loginResult = login(email, PASSWORD);
			String accessToken = accessTokenOf(loginResult);
			Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
			Member admin = memberRepository.save(
					MemberFixture.createAdmin("session-revoke-admin-" + UUID.randomUUID() + "@groove.com"));
			String adminToken = jwtProvider.createAccessToken(admin.getId(), MemberRole.ADMIN);
			sleepUntilNextSecond();

			// when
			mockMvc.perform(patch("/api/v1/admin/members/" + memberId + "/status")
							.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(
									new AdminMemberStatusChangeRequest(MemberStatus.SUSPENDED, null))))
					.andExpect(status().isOk());

			// then
			mockMvc.perform(get("/api/v1/members/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.error.code", is("AUTH_TOKEN_REVOKED")));

			mockMvc.perform(post("/api/v1/auth/reissue").cookie(refreshCookie))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code", is("AUTH_MEMBER_SUSPENDED")));
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

	private String accessTokenOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken")
				.asText();
	}

	/** iat 가 초 단위라 로그인과 폐기가 같은 초에 겹치면 폐기 이전 토큰까지 통과해 검증이 흔들린다. */
	private void sleepUntilNextSecond() throws InterruptedException {
		long millisIntoSecond = System.currentTimeMillis() % 1000;
		Thread.sleep(1000 - millisIntoSecond + 50);
	}
}
