package com.groove.catalog.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Discogs 개인 토큰 연동 설정. */
@ConfigurationProperties(prefix = "discogs")
public record DiscogsProperties(String baseUrl, String token, String userAgent,
		@DefaultValue("5s") Duration readTimeout, @DefaultValue Retry retry) {

	public DiscogsProperties {
		// @ConfigurationProperties 는 미해석 ${...} 를 예외 없이 리터럴로 바인딩한다. 시크릿 누락은 여기서만 걸러진다.
		if (token == null || token.isBlank() || token.startsWith("${")) {
			throw new IllegalStateException("DISCOGS_TOKEN 환경변수가 필요합니다.");
		}
		if (retry == null) {
			retry = new Retry(2, Duration.ofMillis(300));
		}
	}

	/** 일시적 통신 오류 재시도. 모든 호출이 GET 이라 멱등하므로 클라이언트 계층에서 짧게 되받는다. */
	public record Retry(@DefaultValue("2") int maxAttempts, @DefaultValue("300ms") Duration backoff) {

		public Retry {
			if (maxAttempts < 1) {
				throw new IllegalStateException("discogs.retry.max-attempts 는 1 이상이어야 합니다.");
			}
		}
	}
}
