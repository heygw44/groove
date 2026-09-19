package com.groove.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화. 한정반 오픈/마감과 PENDING 주문 만료가 여기서 도는 타이머를 쓴다.
 * 스케줄러는 전부 MySQL named lock({@code NamedLock})으로 인스턴스 간 단일 실행을 보장한다.
 * ShedLock 을 안 쓰는 이유: 이미 있는 락 유틸로 충분하고 테이블·의존성이 늘어난다.
 * test 프로파일에서는 끈다. 백그라운드 실행이 테스트 상태를 오염시키므로 스케줄러는 직접 호출로 검증한다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
