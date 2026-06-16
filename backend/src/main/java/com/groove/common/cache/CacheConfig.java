package com.groove.common.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Cache 추상화 활성화 (#236). Caffeine 로컬 캐시로 카탈로그 읽기 경로(앨범 상세/랜딩 목록)를
 * 서빙한다 — Redis 는 #210 에서 컷, 단일 인스턴스라 in-process 캐시로 충분하다.
 *
 * <p>캐시 이름·스펙(maximumSize/expireAfterWrite/recordStats)은 {@code spring.cache.*} 자동구성으로
 * 주입되므로(application.yaml) 여기서 {@code CacheManager} 빈을 직접 정의하지 않는다 —
 * {@code spring-boot-starter-cache} + Caffeine 이 classpath 에 있으면 {@code CaffeineCacheManager} 가
 * 자동구성된다.
 *
 * <p>{@code order = LOWEST_PRECEDENCE - 1} 로 캐시 advice 를 {@code @Transactional}(기본 LOWEST_PRECEDENCE)
 * 바깥에 둔다 — 캐시 적중 시 DB 트랜잭션을 아예 열지 않아 커넥션 점유를 피한다([[project_test_context_connection_ceiling]]
 * 의 커넥션 천장과도 무관하게 적중 경로가 풀을 건드리지 않는다).
 *
 * <p>주의: 앱 전역 {@code @EnableCaching} 은 여기 한 곳뿐이다 — 도메인별로 중복 선언하지 말 것
 * ({@link com.groove.common.scheduling.SchedulingConfig} 의 {@code @EnableScheduling} 과 동일 원칙).
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig {
}
