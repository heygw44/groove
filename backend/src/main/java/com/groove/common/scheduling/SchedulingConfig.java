package com.groove.common.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 앱 전역 {@code @Scheduled} 활성화 (#W7-2 에서 도입).
 *
 * <p>최초 소비처는 멱등성 레코드 TTL 정리 태스크다. 결제 웹훅 폴링(#W7-4)·배송 스케줄러(#W7-6) 등
 * 후속 {@code @Scheduled} 태스크도 이 활성화를 재사용한다 — 자체 {@code @EnableScheduling} 을 두지 말 것.
 *
 * <p>#W7-1 의 {@code paymentTaskScheduler}({@code ThreadPoolTaskScheduler}) 는 웹훅 콜백 단발 지연
 * 실행 전용으로 별개이며, 본 활성화와 무관하게 유지된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {
}
