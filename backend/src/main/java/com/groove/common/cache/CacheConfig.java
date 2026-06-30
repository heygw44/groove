package com.groove.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring Cache 추상화 활성화(앱 전역 @EnableCaching 은 여기 한 곳뿐). 카탈로그 읽기 경로(앨범 상세/랜딩 목록)를 서빙한다.
 * 단일 인스턴스(local/dev/test)는 in-process Caffeine, 멀티노드(docker/prod)는 공유 Redis 로 노드 간 무효화를 일관시킨다(#366).
 * 캐시 타입·TTL·key-prefix·직렬화는 모두 spring.cache 자동구성(yaml)에서 주입한다 — Redis value 직렬화는 Boot 기본 JDK.
 *
 * order = LOWEST_PRECEDENCE - 1 로 캐시 advice 를 @Transactional 바깥에 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * 캐시 장애를 가용성 위로 끌어올리지 않는다 — Redis 일시 단절이나 역직렬화 실패(롤링 배포 중 클래스 스큐 등)를
     * 캐시 미스로 강등한다. get 실패는 DB 폴백, put/evict/clear 실패는 로깅 후 무시한다(가용성 > 캐시 적중).
     * evict 실패로 남는 stale 은 TTL(60s) 백스톱과 재고 변경 TransactionalEventListener 가 이중으로 정리한다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 get 실패 → DB 폴백 cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("캐시 put 실패 무시 cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 evict 실패 무시 cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("캐시 clear 실패 무시 cache={}", cache.getName(), exception);
            }
        };
    }
}
