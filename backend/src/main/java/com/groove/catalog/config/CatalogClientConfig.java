package com.groove.catalog.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import com.groove.catalog.client.Sleeper;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

@Configuration
@EnableConfigurationProperties({DiscogsProperties.class, CatalogImportProperties.class})
public class CatalogClientConfig {

	@Bean
	public RestClient discogsRestClient(RestClient.Builder builder, DiscogsProperties properties) {
		return builder.baseUrl(properties.baseUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Discogs token=" + properties.token())
				.defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
				.build();
	}

	/** 운영 빈은 실제로 스레드를 재운다. interrupt 되면 레이트리밋 예외로 바꿔 상위에서 재시도 판단을 하게 한다. */
	@Bean
	public Sleeper sleeper() {
		return duration -> {
			try {
				Thread.sleep(duration.toMillis());
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new BusinessException(ErrorCode.CATALOG_RATE_LIMITED, "레이트리밋 대기 중 인터럽트되었습니다.");
			}
		};
	}
}
