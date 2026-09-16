package com.groove.global.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;

import com.groove.support.IntegrationTestSupport;

class HealthIndicatorRegistrationTest extends IntegrationTestSupport {

	@Autowired
	private HealthContributorRegistry healthContributorRegistry;

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
}
