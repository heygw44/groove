package com.groove.global.health;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@ActiveProfiles("test")
class HealthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Nested
	@DisplayName("GET /api/v1/health")
	class Health {

		@Test
		@DisplayName("인증 없이 호출하면 200 과 공통 응답 포맷을 반환한다")
		void returnsOkEnvelopeWithoutAuth() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/health"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.status", is("UP")))
					.andExpect(jsonPath("$.error").doesNotExist())
					.andExpect(jsonPath("$.timestamp").exists());
		}

		@Test
		@DisplayName("허용되지 않은 메서드로 호출하면 405 와 COMMON_METHOD_NOT_ALLOWED 를 반환한다")
		void returnsMethodNotAllowedForPost() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/health"))
					.andExpect(status().isMethodNotAllowed())
					.andExpect(jsonPath("$.success", is(false)))
					.andExpect(jsonPath("$.error.code", is("COMMON_METHOD_NOT_ALLOWED")));
		}
	}

	@Nested
	@DisplayName("보호 경로")
	class ProtectedPath {

		@Test
		@DisplayName("인증 없이 호출하면 401 과 AUTH_UNAUTHORIZED 를 반환한다")
		void returnsUnauthorizedWithoutAuth() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/members/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.success", is(false)))
					.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
		}
	}
}
