package com.groove.common.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * 앱 전역 @Scheduled 활성화. test 프로파일에서는 비활성화한다 — 타이머 백그라운드 잡(아웃박스 릴레이·결제
 * 폴링·배송 진행 등)이 공유 SessionFactory 로 전역 Hibernate statistics 를 오염시키면 N+1 통계 테스트
 * (prepareStatementCount 단언)가 간헐 실패한다. 스케줄러 빈은 남으므로 필요한 테스트는 메서드를 직접 호출해
 * 결정적으로 검증한다. 운영/dev/local/docker 는 영향 없음.
 *
 * @EnableSchedulerLock(ShedLock) 으로 다중 인스턴스에서 각 배치가 노드 간 1회만 실행되게 한다(#365). 이 설정도
 * !test 에만 두므로 test 에선 @SchedulerLock 이 무력(no-op)이라 직접 호출 테스트는 락 없이 그대로 검증된다.
 * defaultLockAtMostFor 는 @SchedulerLock 에 lockAtMostFor 가 없을 때의 폴백(여기선 전부 명시하므로 안전망).
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    /** .usingDbTime(): lock_until/locked_at 을 DB 서버 클록으로 계산해 노드 간 시계 오차를 배제한다. */
    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
