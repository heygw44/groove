package com.groove.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 앱 전역 @Scheduled 활성화. test 프로파일에서는 비활성화한다(#333) — 타이머 백그라운드 잡(아웃박스 릴레이·결제
 * 폴링·배송 진행 등)이 공유 SessionFactory 로 전역 Hibernate statistics 를 오염시키면 N+1 통계 테스트
 * (prepareStatementCount 단언)가 간헐 실패한다. 스케줄러 빈은 남으므로 필요한 테스트는 메서드를 직접 호출해
 * 결정적으로 검증한다. 운영/dev/local/docker 는 영향 없음.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
