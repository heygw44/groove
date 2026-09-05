package com.groove.global.config;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class SwaggerConfigTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Nested
	@DisplayName("GET /v3/api-docs/public")
	class PublicApiDocs {

		@Test
		@DisplayName("bearer 보안 스킴을 전역으로 노출한다")
		void exposesBearerSchemeGlobally() throws Exception {
			// when & then
			mockMvc.perform(get("/v3/api-docs/public"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme", is("bearer")))
					.andExpect(jsonPath("$.security[0].bearerAuth").exists());
		}

		@Test
		@DisplayName("헬스체크 오퍼레이션은 보안 요구사항이 비어 있다")
		void clearsSecurityForHealth() throws Exception {
			// when & then
			mockMvc.perform(get("/v3/api-docs/public"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.paths['/api/v1/health'].get.security", empty()));
		}
	}
}
