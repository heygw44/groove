package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * /actuator/prometheus 스크레이프 전용 Basic 인증 자격증명 (#343). application.yaml 의 metrics.scrape.* 와 매핑된다.
 * <p>
 * /actuator/prometheus 는 nginx 가 외부로 위임하므로(공개 도달) JWT 본 체인과 분리된 체인 로컬 Basic 인증으로
 * 보호한다(SecurityConfig#metricsScrapeSecurityFilterChain). 데모 기본값을 제공하되 운영에서는
 * METRICS_SCRAPE_USERNAME / METRICS_SCRAPE_PASSWORD 로 강한 값을 주입한다.
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
