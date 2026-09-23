package com.groove.global.alert;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 정합성 경보를 Slack 수신 웹훅으로 보낸다. 전송은 전용 executor 스레드에서 비동기로 하고, 실패해도 호출부에는
 * 전파하지 않는다(경보 실패가 또 다른 경보를 부르면 안 된다). Slack 수신 웹훅은 초당 1회 제한(429 +
 * Retry-After)이라 executor 스레드가 마지막 전송 시각을 기준으로 최소 1초 간격을 스스로 지킨다 - executor 가
 * core=max=1 이라 이 지연이 다른 경보 전송을 밀어낼 뿐, 호출자 스레드를 막지 않는다.
 */
@Slf4j
public class SlackWebhookAlertNotifier implements AlertNotifier {

	private static final int CONTENT_LIMIT = 3_000;
	private static final long MIN_INTERVAL_MILLIS = 1_000;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final RestClient restClient;
	private final String webhookUrl;
	private final Executor executor;
	private final AlertThrottle throttle;
	private final Clock clock;
	private final String serviceLabel;
	private final AtomicLong lastSentAtMillis = new AtomicLong();

	public SlackWebhookAlertNotifier(RestClient restClient, String webhookUrl, Executor executor,
			AlertThrottle throttle, Clock clock, String serviceLabel) {
		this.restClient = restClient;
		this.webhookUrl = webhookUrl;
		this.executor = executor;
		this.throttle = throttle;
		this.clock = clock;
		this.serviceLabel = serviceLabel;
	}

	@Override
	public void notify(Alert alert) {
		AlertThrottle.ThrottleResult result = throttle.acquire(alert);
		if (!result.acquired()) {
			log.debug("경보 억제 창 안이라 건너뛴다 key={}", alert.key());
			return;
		}
		String text = buildText(alert, result.carried());
		executor.execute(() -> post(text, alert.key()));
	}

	@Override
	public void flushSuppressed() {
		for (SuppressedSummary summary : throttle.drainExpired()) {
			executor.execute(() -> post(buildSummaryText(summary), summary.key()));
		}
	}

	private String buildText(Alert alert, SuppressedSummary carried) {
		StringBuilder text = new StringBuilder();
		text.append(alert.severity() == AlertSeverity.CRITICAL ? "🔴 [CRITICAL] " : "🟠 [WARN] ")
				.append(alert.key())
				.append('\n')
				.append(alert.summary());
		if (alert.targetId() != null) {
			text.append('\n').append("대상: ").append(alert.targetId());
		}
		if (carried != null) {
			text.append('\n').append(carried.describe());
		}
		text.append('\n')
				.append(serviceLabel)
				.append(" · ")
				.append(LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER))
				.append(" KST");
		return truncate(text.toString());
	}

	private String buildSummaryText(SuppressedSummary summary) {
		StringBuilder text = new StringBuilder();
		text.append(summary.severity() == AlertSeverity.CRITICAL ? "🔴 [CRITICAL] " : "🟠 [WARN] ")
				.append(summary.key())
				.append(" (억제 요약)")
				.append('\n')
				.append(summary.describe())
				.append('\n')
				.append(serviceLabel)
				.append(" · ")
				.append(LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER))
				.append(" KST");
		return truncate(text.toString());
	}

	private String truncate(String text) {
		return text.length() > CONTENT_LIMIT ? text.substring(0, CONTENT_LIMIT) : text;
	}

	private void post(String text, String key) {
		waitForMinInterval();
		try {
			restClient.post()
					.uri(webhookUrl)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("text", text))
					.retrieve()
					.toBodilessEntity();
		} catch (RuntimeException e) {
			log.warn("경보 전송 실패 key={}", key, e);
		} finally {
			lastSentAtMillis.set(System.currentTimeMillis());
		}
	}

	/**
	 * 전송 페이스 조절은 실제 경과 시간(wall clock) 기준이다 - 메시지 본문의 KST 표기에 쓰는 {@code clock}
	 * 은 테스트에서 고정값으로 주입되므로 여기 섞으면 매 호출이 1초씩 실제로 잠드는 게 아니라 영원히 같은 간격으로
	 * 계산돼 버린다.
	 */
	private void waitForMinInterval() {
		long elapsed = System.currentTimeMillis() - lastSentAtMillis.get();
		long remaining = MIN_INTERVAL_MILLIS - elapsed;
		if (remaining <= 0) {
			return;
		}
		try {
			Thread.sleep(remaining);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
