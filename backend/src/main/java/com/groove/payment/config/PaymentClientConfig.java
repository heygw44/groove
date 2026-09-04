package com.groove.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 토스 결제 API 전용 {@link RestClient}. 결제 승인이 걸려 있는 요청 스레드를 오래 잡지 않도록 타임아웃을 짧게 둔다.
 * Basic 인증 헤더는 요청마다 {@code TossPaymentClient} 가 붙인다.
 */
@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class PaymentClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	@Bean
	public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(CONNECT_TIMEOUT)
				.withReadTimeout(READ_TIMEOUT);
		return builder.baseUrl(properties.baseUrl())
				.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
				.build();
	}
}
