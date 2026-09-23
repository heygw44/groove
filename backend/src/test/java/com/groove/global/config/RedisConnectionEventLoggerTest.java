package com.groove.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.lettuce.core.event.connection.ConnectEvent;
import io.lettuce.core.event.connection.ConnectionActivatedEvent;
import io.lettuce.core.event.connection.ConnectionDeactivatedEvent;
import io.lettuce.core.event.connection.ReconnectAttemptEvent;
import io.lettuce.core.event.connection.ReconnectFailedEvent;
import io.lettuce.core.resource.ClientResources;

@ExtendWith(OutputCaptureExtension.class)
class RedisConnectionEventLoggerTest {

	private ClientResources clientResources;
	private RedisConnectionEventLogger logger;
	private Logger loggerUnderTest;
	private Level originalLevel;

	@BeforeEach
	void setUp() {
		clientResources = ClientResources.create();
		logger = new RedisConnectionEventLogger(clientResources);
		logger.init();

		// DEBUG 로그 검증을 ambient 로깅 설정에 기대지 않도록 대상 로거 레벨을 직접 강제한다.
		loggerUnderTest = (Logger) LoggerFactory.getLogger(RedisConnectionEventLogger.class);
		originalLevel = loggerUnderTest.getLevel();
		loggerUnderTest.setLevel(Level.DEBUG);
	}

	@AfterEach
	void tearDown() {
		logger.destroy();
		clientResources.shutdown(0, 0, TimeUnit.MILLISECONDS);
		loggerUnderTest.setLevel(originalLevel);
	}

	@Nested
	@DisplayName("onEvent()")
	class OnEvent {

		@Test
		@DisplayName("연결 활성화 이벤트를 INFO 로 남긴다")
		void logsConnectionActivatedAsInfo(CapturedOutput output) {
			// given
			InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 6379);

			// when
			clientResources.eventBus().publish(new ConnectionActivatedEvent(remote, remote));

			// then
			await().atMost(Duration.ofSeconds(2))
					.untilAsserted(() -> assertThat(output.getOut()).contains("Redis 연결 활성화").contains("127.0.0.1"));
		}

		@Test
		@DisplayName("연결 종료 이벤트를 INFO 로 남긴다")
		void logsConnectionDeactivatedAsInfo(CapturedOutput output) {
			// given
			InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 6379);

			// when
			clientResources.eventBus().publish(new ConnectionDeactivatedEvent(remote, remote));

			// then
			await().atMost(Duration.ofSeconds(2))
					.untilAsserted(() -> assertThat(output.getOut()).contains("Redis 연결 종료").contains("127.0.0.1"));
		}

		@Test
		@DisplayName("재연결 실패 이벤트를 attempt 와 cause 메시지를 담아 INFO 로 남긴다")
		void logsReconnectFailedWithAttemptAndCause(CapturedOutput output) {
			// given
			InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 6379);
			RuntimeException cause = new RuntimeException("connection refused");

			// when
			clientResources.eventBus().publish(new ReconnectFailedEvent(remote, remote, cause, 3));

			// then
			await().atMost(Duration.ofSeconds(2))
					.untilAsserted(() -> assertThat(output.getOut())
							.contains("Redis 재연결 실패")
							.contains("attempt=3")
							.contains("connection refused"));
		}

		@Test
		@DisplayName("재연결 시도 이벤트를 attempt 를 담아 DEBUG 로 남긴다")
		void logsReconnectAttemptAsDebug(CapturedOutput output) {
			// given
			InetSocketAddress remote = new InetSocketAddress("127.0.0.1", 6379);

			// when
			clientResources.eventBus().publish(new ReconnectAttemptEvent(remote, remote, 2));

			// then
			await().atMost(Duration.ofSeconds(2))
					.untilAsserted(() -> assertThat(output.getOut()).contains("Redis 재연결 시도").contains("attempt=2"));
		}

		@Test
		@DisplayName("목록에 없는 이벤트는 남기지 않는다")
		void ignoresUnlistedEvents(CapturedOutput output) throws InterruptedException {
			// given & when
			clientResources.eventBus().publish(new ConnectEvent("redis://localhost:6379", "epId"));
			Thread.sleep(200);

			// then
			assertThat(output.getOut()).doesNotContain("Redis 연결").doesNotContain("Redis 재연결");
		}
	}
}
