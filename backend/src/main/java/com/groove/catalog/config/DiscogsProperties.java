package com.groove.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Discogs 개인 토큰 연동 설정. */
@ConfigurationProperties(prefix = "discogs")
public record DiscogsProperties(String baseUrl, String token, String userAgent) {

	public DiscogsProperties {
		// @ConfigurationProperties 는 미해석 ${...} 를 예외 없이 리터럴로 바인딩한다. 시크릿 누락은 여기서만 걸러진다.
		if (token == null || token.isBlank() || token.startsWith("${")) {
			throw new IllegalStateException("DISCOGS_TOKEN 환경변수가 필요합니다.");
		}
	}
}
