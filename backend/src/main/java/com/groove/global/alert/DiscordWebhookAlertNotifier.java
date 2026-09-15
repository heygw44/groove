package com.groove.global.alert;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executor;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/** 정합성 경보를 Discord 웹훅으로 보낸다. 전송은 비동기이고, 실패해도 호출부에는 전파하지 않는다(경보 실패가 경보를 부르면 안 된다). */
@Slf4j
public class DiscordWebhookAlertNotifier implements AlertNotifier {

	// Discord 메시지 본문 상한.
	private static final int CONTENT_LIMIT = 2000;
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final RestClient restClient;
	private final String webhookUrl;
	private final Executor executor;
	private final AlertThrottle throttle;
	private final Clock clock;
	private final String serviceLabel;

	public DiscordWebhookAlertNotifier(RestClient restClient, String webhookUrl, Executor executor,
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
		if (!throttle.tryAcquire(alert.key())) {
			log.debug("경보 억제 창 안이라 건너뛴다 key={}", alert.key());
			return;
		}
		String content = buildContent(alert);
		executor.execute(() -> post(content, alert.key()));
	}

	private String buildContent(Alert alert) {
		StringBuilder content = new StringBuilder();
		content.append(alert.severity() == AlertSeverity.CRITICAL ? "🔴 [CRITICAL] " : "🟠 [WARN] ")
				.append(alert.key())
				.append('\n')
				.append(alert.summary());
		if (alert.targetId() != null) {
			content.append('\n').append("대상: ").append(alert.targetId());
		}
		content.append('\n')
				.append(serviceLabel)
				.append(" · ")
				.append(LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER))
				.append(" KST");
		String result = content.toString();
		return result.length() > CONTENT_LIMIT ? result.substring(0, CONTENT_LIMIT) : result;
	}

	private void post(String content, String key) {
		try {
			restClient.post()
					.uri(webhookUrl)
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("content", content))
					.retrieve()
					.toBodilessEntity();
		} catch (RuntimeException e) {
			log.warn("경보 전송 실패 key={}", key, e);
		}
	}
}
