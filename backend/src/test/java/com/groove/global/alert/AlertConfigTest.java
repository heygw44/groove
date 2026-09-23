package com.groove.global.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

class AlertConfigTest {

	private final AlertConfig alertConfig = new AlertConfig();

	@Nested
	@DisplayName("alertNotifier()")
	class AlertNotifierBean {

		@Test
		@DisplayName("웹훅 URL 이 비어 있으면 LoggingAlertNotifier 를 만든다")
		void createsLoggingNotifierWhenUrlBlank() {
			// given
			AlertProperties properties = new AlertProperties("", Duration.ofMinutes(5), 100, Duration.ofSeconds(2),
					Duration.ofSeconds(60));

			// when
			AlertNotifier notifier = alertConfig.alertNotifier(properties, RestClient.create(), Runnable::run,
					Clock.systemUTC(), new MockEnvironment());

			// then
			assertThat(notifier).isInstanceOf(LoggingAlertNotifier.class);
		}

		@Test
		@DisplayName("웹훅 URL 이 있으면 SlackWebhookAlertNotifier 를 만든다")
		void createsSlackNotifierWhenUrlPresent() {
			// given
			AlertProperties properties = new AlertProperties("https://hooks.slack.com/services/test",
					Duration.ofMinutes(5), 100, Duration.ofSeconds(2), Duration.ofSeconds(60));

			// when
			AlertNotifier notifier = alertConfig.alertNotifier(properties, RestClient.create(), Runnable::run,
					Clock.systemUTC(), new MockEnvironment());

			// then
			assertThat(notifier).isInstanceOf(SlackWebhookAlertNotifier.class);
		}
	}
}
