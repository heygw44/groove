package com.groove.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * prod 전용 메트릭 스크레이프 비밀번호 가드. /actuator/prometheus 는 nginx 가 외부 위임해 도달하고 Basic
 * 체인이 유일한 보호라, 데모 기본값('change-me-demo')으로 기동하면 레포 공개 비번으로 메트릭이 노출된다.
 * 검사는 노출 조건부 — exposure.include 에 prometheus 가 없으면 통과, 있으면 blank·플레이스홀더 거부 fail-fast.
 * 약한값 거부를 prod 전용으로 두는 이유는 DbSecretGuard 와 동일(데모는 약한 비번을 정당하게 씀).
 */
@Component
@Profile("prod")
public class MetricsScrapeSecretGuard implements InitializingBean {

    private final Environment environment;

    public MetricsScrapeSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isPrometheusExposed()) {
            // prometheus 미노출이면 스크레이프 비번은 의미 없다 — 운영이 prometheus 를 안 쓰는데 비번 주입을 강제하지 않는다.
            return;
        }
        String password = environment.getProperty("metrics.scrape.password");
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "metrics.scrape.password (METRICS_SCRAPE_PASSWORD) 가 비어 있습니다 — prometheus 노출 시 강력한 고유 값을 주입하세요 (#343)");
        }
        // 'change-me-demo' 데모 기본값은 'change-me' 마커로 여기서 거부된다.
        SecretPlaceholderGuard.rejectPlaceholder("metrics.scrape.password", password);
    }

    private boolean isPrometheusExposed() {
        String include = environment.getProperty("management.endpoints.web.exposure.include", "");
        return Arrays.stream(include.split(","))
                .map(token -> token.strip().toLowerCase(Locale.ROOT))
                .anyMatch(token -> token.equals("prometheus") || token.equals("*"));
    }
}
