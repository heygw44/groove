package com.groove.common.cache;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Cache 추상화 활성화. Caffeine 로컬 캐시로 카탈로그 읽기 경로(앨범 상세/랜딩 목록)를 서빙한다.
 *
 * <p>캐시 이름·스펙(maximumSize/expireAfterWrite/recordStats)은 spring.cache 자동구성으로
 * 주입되며 CacheManager 빈을 직접 정의하지 않는다.
 *
 * <p>order = LOWEST_PRECEDENCE - 1 로 캐시 advice 를 @Transactional 바깥에 둔다.
 *
 * <p>앱 전역 @EnableCaching 은 여기 한 곳뿐이다.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig {
}
