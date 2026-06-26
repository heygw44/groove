package com.groove.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * prod 전용 — 약한 DB 시크릿으로 기동하는 것을 거부하는 가드(이슈 #321). docker/local 데모는 약한
 * 데모 비밀번호(changeme/rootpw)를 정당하게 쓰므로 약한값 거부는 반드시 prod 전용이다(전역이면 데모 기동이
 * 깨진다). 부적합 시 afterPropertiesSet 에서 IllegalStateException 으로 fail-fast.
 *
 * 검사 대상:
 * - spring.datasource.password(=DB_PASSWORD) — blank·약한값·플레이스홀더 거부.
 * - MYSQL_ROOT_PASSWORD — docker-compose mysql 전용이라 부재(null)는 통과(관리형 DB 등)하되,
 *   존재하면 빈 값 포함 검사한다(시크릿 게이트 우회 방지).
 *
 * 런북은 docs/ARCHITECTURE.md §10.6.
 */
@Component
@Profile("prod")
public class DbSecretGuard implements InitializingBean {

    private final Environment environment;

    public DbSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        // DB_PASSWORD 자체가 미설정이면 base application.yaml 의 폴백 없는 ${DB_PASSWORD} 가 프로퍼티 해석
        // 단계에서 이미 기동을 막는다(#270). 아래 blank 검사는 빈 문자열(DB_PASSWORD=)로 주입된 경우의 방어선.
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (!StringUtils.hasText(dbPassword)) {
            throw new IllegalStateException(
                    "spring.datasource.password (DB_PASSWORD) 가 비어 있습니다 — 운영 배포 전 강력한 고유 값을 주입하세요 (이슈 #321)");
        }
        SecretPlaceholderGuard.rejectWeakDbPassword("spring.datasource.password", dbPassword);

        // 부재(null)는 통과(관리형 DB 등). 그러나 빈 문자열(MYSQL_ROOT_PASSWORD=)로 주입되면 약한값보다 더
        // 위험하므로 명시적으로 거부한다 — hasText 만으로 건너뛰면 빈 값이 게이트를 우회한다.
        String rootPassword = environment.getProperty("MYSQL_ROOT_PASSWORD");
        if (rootPassword != null) {
            if (!StringUtils.hasText(rootPassword)) {
                throw new IllegalStateException(
                        "MYSQL_ROOT_PASSWORD 가 비어 있습니다 — 운영 배포 전 강력한 고유 값을 주입하세요 (이슈 #321)");
            }
            SecretPlaceholderGuard.rejectWeakDbPassword("MYSQL_ROOT_PASSWORD", rootPassword);
        }
    }
}
