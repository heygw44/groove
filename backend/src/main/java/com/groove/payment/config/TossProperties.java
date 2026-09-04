package com.groove.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스 페이먼츠 연동 설정. secret-key 는 환경변수로만 주입한다. */
@ConfigurationProperties(prefix = "toss")
public record TossProperties(String clientKey, String secretKey, String baseUrl) {

	public TossProperties {
		// @ConfigurationProperties 는 미해석 ${...} 를 예외 없이 리터럴로 바인딩한다. 시크릿 누락은 여기서만 걸러진다.
		if (secretKey == null || secretKey.isBlank() || secretKey.startsWith("${")) {
			throw new IllegalStateException("TOSS_SECRET_KEY 환경변수가 필요합니다.");
		}
	}
}
