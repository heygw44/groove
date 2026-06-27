package com.groove.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * prod 전용 — prometheus 메트릭 엔드포인트를 노출하면서 약한/플레이스홀더 스크레이프 비밀번호로 기동하는 것을
 * 거부하는 가드(#343). /actuator/prometheus 는 nginx 가 외부로 위임해 도달 가능하므로(메트릭 전용 Basic 체인이
 * 유일한 보호), prod 에서 노출 시 데모 기본값('change-me-demo')이 그대로 쓰이면 레포 공개 비번으로 메트릭이
 * 노출된다. DbSecretGuard(#321)와 동일 기조 — 약한값 거부는 반드시 prod 전용이다(docker/local 데모는 데모
 * 비번을 정당하게 쓴다).
 * <p>
 * 검사는 노출 조건부 — management.endpoints.web.exposure.include 에 prometheus(또는 *)가 없으면 비번은
 * 무의미하므로 통과한다. 노출 시에만 blank·플레이스홀더('change-me' 마커 등)를 거부해 fail-fast 한다.
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
