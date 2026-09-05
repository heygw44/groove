package com.groove.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TossPropertiesTest.TestConfig.class)
@ActiveProfiles("test")
class TossPropertiesTest {

	private static final String BASE_URL = "https://api.tosspayments.com";

	@Autowired
	TossProperties tossProperties;

	@Nested
	@DisplayName("바인딩")
	class Binding {

		@Test
		@DisplayName("test 프로파일이면 더미 키와 토스 base-url 이 바인딩된다")
		void bindsDummyKeysFromTestProfile() {
			// when & then
			assertThat(tossProperties.clientKey()).isEqualTo("test_ck_dummy");
			assertThat(tossProperties.secretKey()).isEqualTo("test_sk_dummy");
			assertThat(tossProperties.baseUrl()).isEqualTo(BASE_URL);
		}
	}

	@Nested
	@DisplayName("secretKey 검증")
	class SecretKeyValidation {

		private final ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(TestConfig.class);

		@ParameterizedTest
		@ValueSource(strings = {"", "   "})
		@DisplayName("시크릿 키가 비어 있으면 컨텍스트 로딩이 실패한다")
		void failsToLoadContextWhenSecretKeyBlank(String secretKey) {
			// when & then
			runner.withPropertyValues("toss.client-key=test_ck_dummy", "toss.secret-key=" + secretKey,
							"toss.base-url=" + BASE_URL)
					.run(context -> assertThat(context).getFailure()
							.hasRootCauseInstanceOf(IllegalStateException.class));
		}

		@Test
		@DisplayName("시크릿 키가 주입되면 컨텍스트가 정상 로딩된다")
		void loadsContextWhenSecretKeyInjected() {
			// when & then
			runner.withPropertyValues("toss.client-key=test_ck_dummy", "toss.secret-key=test_sk_dummy",
							"toss.base-url=" + BASE_URL)
					.run(context -> assertThat(context).hasNotFailed());
		}

		@Test
		@DisplayName("환경변수가 없어 ${...} 리터럴이 그대로 들어오면 예외를 던진다")
		void throwsWhenSecretKeyIsUnresolvedPlaceholder() {
			// when & then
			assertThatThrownBy(() -> new TossProperties("test_ck_dummy", "${TOSS_SECRET_KEY}", BASE_URL))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("TOSS_SECRET_KEY");
		}
	}

	@Configuration
	@EnableConfigurationProperties(TossProperties.class)
	static class TestConfig {
	}
}
