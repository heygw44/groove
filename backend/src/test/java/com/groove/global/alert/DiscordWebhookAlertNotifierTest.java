package com.groove.global.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class DiscordWebhookAlertNotifierTest {

	private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/test";

	private MockRestServiceServer server;
	private RestClient restClient;
	private Clock clock;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		restClient = builder.build();
		clock = Clock.fixed(Instant.parse("2026-09-15T05:03:11Z"), ZoneId.of("Asia/Seoul"));
	}

	private DiscordWebhookAlertNotifier notifier(AlertThrottle throttle) {
		return new DiscordWebhookAlertNotifier(restClient, WEBHOOK_URL, Runnable::run, throttle, clock,
				"groove-prod");
	}

	private AlertThrottle throttle() {
		return new AlertThrottle(clock, Duration.ofMinutes(5));
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Nested
	@DisplayName("notify()")
	class Notify {

		@Test
		@DisplayName("경보를 Discord 웹훅 포맷으로 보낸다")
		void postsDiscordContent() {
			// given
			String expectedContent = "🔴 [CRITICAL] limited.redis-circuit-open\n"
					+ "한정반 Redis 서킷 OPEN, DB 폴백 전환\n"
					+ "대상: dropId=12 memberId=34\n"
					+ "groove-prod · 2026-09-15 14:03:11 KST";
			server.expect(requestTo(WEBHOOK_URL))
					.andExpect(method(HttpMethod.POST))
					.andExpect(jsonPath("$.content").value(expectedContent))
					.andRespond(withSuccess());

			// when
			notifier(throttle()).notify(Alert.critical("limited.redis-circuit-open",
					"한정반 Redis 서킷 OPEN, DB 폴백 전환", "dropId=12 memberId=34"));

			// then
			server.verify();
		}

		@Test
		@DisplayName("targetId 가 없으면 대상 줄을 뺀다")
		void omitsTargetLineWhenTargetIdIsNull() {
			// given
			String expectedContent = "🟠 [WARN] catalog.resync-budget-exceeded\n"
					+ "재검증 주기가 신선도 TTL 을 초과한다\n"
					+ "groove-prod · 2026-09-15 14:03:11 KST";
			server.expect(requestTo(WEBHOOK_URL))
					.andExpect(jsonPath("$.content").value(expectedContent))
					.andRespond(withSuccess());

			// when
			notifier(throttle()).notify(Alert.warn("catalog.resync-budget-exceeded", "재검증 주기가 신선도 TTL 을 초과한다", null));

			// then
			server.verify();
		}

		@Test
		@DisplayName("같은 키는 억제 창 안에서 한 번만 보낸다")
		void sendsOnceWithinSuppressWindow() {
			// given
			server.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess());
			DiscordWebhookAlertNotifier notifier = notifier(throttle());
			Alert alert = Alert.warn("limited.release-failed", "선점 해제 실패", "dropId=1 memberId=2");

			// when
			notifier.notify(alert);
			notifier.notify(alert);

			// then
			server.verify();
		}

		@Test
		@DisplayName("웹훅 전송이 실패해도 예외를 삼킨다")
		void swallowsWebhookFailure() {
			// given
			server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());
			DiscordWebhookAlertNotifier notifier = notifier(throttle());

			// when & then
			assertThatCode(() -> notifier.notify(Alert.warn("limited.release-failed", "선점 해제 실패", null)))
					.doesNotThrowAnyException();
			server.verify();
		}

		@Test
		@DisplayName("내용이 2000자를 넘으면 잘라낸다")
		void truncatesContentOver2000Chars() throws Exception {
			// given
			String longSummary = "가".repeat(3000);
			server.expect(requestTo(WEBHOOK_URL))
					.andExpect(request -> {
						String body = ((MockClientHttpRequest)request).getBodyAsString();
						JsonNode json = new ObjectMapper().readTree(body);
						assertThat(json.get("content").asText()).hasSize(2000);
					})
					.andRespond(withSuccess());
			DiscordWebhookAlertNotifier notifier = notifier(throttle());

			// when
			notifier.notify(Alert.warn("limited.release-failed", longSummary, null));

			// then
			server.verify();
		}

		@Test
		@DisplayName("실행기 큐가 가득 차면 경보를 버리고 거절 핸들러가 카운트한다")
		void dropsWhenQueueIsFull() {
			// given
			CountDownLatch blockLatch = new CountDownLatch(1);
			server.expect(requestTo(WEBHOOK_URL)).andRespond(request -> {
				awaitQuietly(blockLatch);
				return withSuccess().createResponse(request);
			});
			server.expect(requestTo(WEBHOOK_URL)).andRespond(withSuccess());

			ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
			executor.setCorePoolSize(1);
			executor.setMaxPoolSize(1);
			executor.setQueueCapacity(1);
			executor.setThreadNamePrefix("alert-queue-test-");
			AtomicInteger rejectedCount = new AtomicInteger();
			executor.setRejectedExecutionHandler((runnable, taskExecutor) -> rejectedCount.incrementAndGet());
			executor.initialize();
			DiscordWebhookAlertNotifier notifier = new DiscordWebhookAlertNotifier(restClient, WEBHOOK_URL, executor,
					throttle(), clock, "groove-prod");

			try {
				// when
				notifier.notify(Alert.warn("queue.k1", "s1", null));
				notifier.notify(Alert.warn("queue.k2", "s2", null));
				notifier.notify(Alert.warn("queue.k3", "s3", null));

				// then
				assertThat(rejectedCount.get()).isEqualTo(1);
			} finally {
				blockLatch.countDown();
				executor.shutdown();
			}
		}
	}
}
