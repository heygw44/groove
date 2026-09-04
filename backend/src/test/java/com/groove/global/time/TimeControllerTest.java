package com.groove.global.time;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import com.groove.auth.jwt.JwtProvider;
import com.groove.global.config.ClockConfig;
import com.groove.global.config.RestAccessDeniedHandler;
import com.groove.global.config.RestAuthenticationEntryPoint;
import com.groove.global.config.SecurityConfig;
import com.groove.global.config.WebConfig;

@WebMvcTest(TimeController.class)
@Import({SecurityConfig.class, WebConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
	JwtProvider.class, ClockConfig.class})
@ActiveProfiles("test")
class TimeControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Nested
	@DisplayName("GET /api/v1/time")
	class GetServerTime {

		@Test
		@DisplayName("인증 없이 호출해도 200 과 오프셋이 포함된 서버 시각을 반환한다")
		void returnsServerTimeWithoutAuth() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/time"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.success", is(true)))
					.andExpect(jsonPath("$.data.serverTime", endsWith("+09:00")));
		}

		@Test
		@DisplayName("캐시되지 않도록 Cache-Control: no-store 를 내려준다")
		void returnsNoStoreCacheControl() throws Exception {
			// when & then
			mockMvc.perform(get("/api/v1/time"))
					.andExpect(status().isOk())
					.andExpect(header().string("Cache-Control", "no-store"));
		}

		@Test
		@DisplayName("허용되지 않은 메서드로 호출하면 405 를 반환한다")
		void returnsMethodNotAllowedForPost() throws Exception {
			// when & then
			mockMvc.perform(post("/api/v1/time"))
					.andExpect(status().isMethodNotAllowed())
					.andExpect(jsonPath("$.error.code", is("COMMON_METHOD_NOT_ALLOWED")));
		}
	}
}
