package com.groove.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러 활성화. 한정반 오픈/마감과 PENDING 주문 만료가 여기서 도는 타이머를 쓴다.
 * 인스턴스가 EC2 한 대라 ShedLock 같은 분산 락은 두지 않는다. 스케일아웃 시점에 도입한다.
 * test 프로파일에서는 끈다. 백그라운드 실행이 테스트 상태를 오염시키므로 스케줄러는 직접 호출로 검증한다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
