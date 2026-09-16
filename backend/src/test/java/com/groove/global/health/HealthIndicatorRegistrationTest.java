package com.groove.global.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.groove.support.IntegrationTestSupport;

@AutoConfigureMockMvc
class HealthIndicatorRegistrationTest extends IntegrationTestSupport {

	@Autowired
	private HealthContributorRegistry healthContributorRegistry;

	@Autowired
	private MockMvc mockMvc;

	@Nested
	@DisplayName("/actuator/health 인디케이터 등록")
	class ContributorRegistration {

		@Test
		@DisplayName("DataSource 가 있으면 db 인디케이터가 자동 등록된다")
		void registersDbContributor() {
			// when & then
			assertThat(healthContributorRegistry.getContributor("db")).isNotNull();
		}

		@Test
		@DisplayName("RedisConnectionFactory 가 있으면 redis 인디케이터가 자동 등록된다")
		void registersRedisContributor() {
			// when & then
			assertThat(healthContributorRegistry.getContributor("redis")).isNotNull();
		}
	}

	@Nested
	@DisplayName("GET /actuator/metrics")
	class MetricsEndpoint {

		@Test
		@DisplayName("인증 없이 호출하면 200 을 반환한다")
		void returnsOkWithoutAuth() throws Exception {
			// when & then
			mockMvc.perform(get("/actuator/metrics"))
					.andExpect(status().isOk());
		}
	}
}
