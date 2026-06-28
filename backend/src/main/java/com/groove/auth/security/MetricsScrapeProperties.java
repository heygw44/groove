package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /actuator/prometheus 스크레이프 전용 Basic 인증 자격증명. nginx 가 외부로 위임해 공개 도달하므로
 * JWT 본 체인과 분리된 체인 로컬 Basic 으로 보호한다. 운영은 METRICS_SCRAPE_USERNAME/PASSWORD 로 강한 값 주입.
 */
@ConfigurationProperties(prefix = "metrics.scrape")
public record MetricsScrapeProperties(
        String username,
        String password
) {

    public MetricsScrapeProperties {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("metrics.scrape.username 은 비어 있을 수 없습니다");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("metrics.scrape.password 는 비어 있을 수 없습니다");
        }
    }
}
