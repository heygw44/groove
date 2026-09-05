package com.groove.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = JwtPropertiesTest.TestConfig.class)
@ActiveProfiles("test")
class JwtPropertiesTest {

	@Autowired
	JwtProperties jwtProperties;

	@Nested
	@DisplayName("바인딩")
	class Binding {

		@Test
		@DisplayName("test 프로파일이면 짧은 만료 시간이 바인딩된다")
		void bindsShortExpiryFromTestProfile() {
			// when & then
			assertThat(jwtProperties.secret()).isEqualTo("test-secret-key-for-jwt-signing-must-be-long-enough-000000");
			assertThat(jwtProperties.accessTokenExpiry()).isEqualTo(Duration.ofMinutes(1));
			assertThat(jwtProperties.refreshTokenExpiry()).isEqualTo(Duration.ofMinutes(10));
		}
	}

	@Configuration
	@EnableConfigurationProperties(JwtProperties.class)
	static class TestConfig {
	}
}
