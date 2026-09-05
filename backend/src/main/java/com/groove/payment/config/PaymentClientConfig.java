package com.groove.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 토스 결제 API 전용 {@link RestClient}. 타임아웃은 {@code spring.http.client.*} 전역 설정으로 잡는다.
 * 이 RestClient 는 토스 전용이라 전역 설정을 그대로 써도 다른 연동과 충돌하지 않고, 자동 구성된
 * {@code RestClient.Builder} 를 그대로 쓰기 때문에 테스트에서 {@code MockServerRestClientCustomizer} 같은
 * 커스터마이저가 그대로 먹힌다. Basic 인증 헤더는 요청마다 {@code TossPaymentClient} 가 붙인다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentClientConfig {

	@Bean
	public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
		return builder.baseUrl(properties.baseUrl()).build();
	}
}
