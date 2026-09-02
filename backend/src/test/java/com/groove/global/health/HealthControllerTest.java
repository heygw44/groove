package com.groove.global.health;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HealthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	@DisplayName("헬스체크_인증없이호출_200과 공통응답포맷")
	void health_withoutAuth_returnsOkEnvelope() throws Exception {
		mockMvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.status", is("UP")))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	@DisplayName("헬스체크_허용되지않은메서드_405와 에러코드")
	void health_wrongMethod_returnsMethodNotAllowedEnvelope() throws Exception {
		mockMvc.perform(post("/api/v1/health"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.error.code", is("COMMON_METHOD_NOT_ALLOWED")));
	}

	@Test
	@DisplayName("보호경로_인증없이호출_401")
	void protectedPath_withoutAuth_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/members/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.error.code", is("AUTH_UNAUTHORIZED")));
	}
}
