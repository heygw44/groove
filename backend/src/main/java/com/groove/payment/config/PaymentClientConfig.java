package com.groove.payment.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 토스 결제 API 전용 {@link RestClient}. 타임아웃은 {@code spring.http.client.*} 전역 설정으로 잡는다.
 * 이 RestClient 는 토스 전용이라 전역 설정을 그대로 써도 다른 연동과 충돌하지 않고, 자동 구성된
 * {@code RestClient.Builder} 를 그대로 쓰기 때문에 테스트에서 {@code MockServerRestClientCustomizer} 같은
 * 커스터마이저가 그대로 먹힌다. Basic 인증 헤더는 요청마다 {@code TossPaymentClient} 가 붙인다.
 */
@Configuration
@EnableConfigurationProperties({
	TossProperties.class, PaymentReconcileProperties.class, PaymentSettlementProperties.class
})
public class PaymentClientConfig {

	@Bean
	public RestClient tossRestClient(RestClient.Builder builder, TossProperties properties) {
		return builder.baseUrl(properties.baseUrl()).build();
	}

	/**
	 * 거래 조회(GET /v1/transactions) 전용 RestClient. 토스 문서상 응답이 최대 60초 걸릴 수 있어
	 * 전역 read-timeout(10s) 을 그대로 쓰면 정상 응답도 타임아웃으로 끊긴다. {@code RestClient.Builder} 는
	 * 프로토타입 빈이라 주입될 때마다 새 인스턴스를 받으므로 {@code tossRestClient} 의 설정과 섞이지 않는다.
	 */
	@Bean
	public RestClient tossTransactionRestClient(RestClient.Builder builder, TossProperties properties,
			ClientHttpRequestFactoryBuilder<?> factoryBuilder, ClientHttpRequestFactorySettings factorySettings) {
		ClientHttpRequestFactory requestFactory = factoryBuilder.build(
				factorySettings.withTimeouts(Duration.ofSeconds(3), Duration.ofSeconds(60)));
		return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}
}
