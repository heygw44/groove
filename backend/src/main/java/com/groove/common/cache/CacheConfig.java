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
 * 카탈로그 읽기 캐시. 단일 인스턴스는 Caffeine, 멀티노드는 공유 Redis(타입·TTL·직렬화는 spring.cache yaml).
 * order = LOWEST_PRECEDENCE - 1 로 캐시 advice 를 @Transactional 바깥에 둔다.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * get/put 실패는 미스로 강등(DB 폴백, 가용성), evict/clear 실패는 전파한다.
     * 전파해도 이미 실패한 evict 의 stale 엔트리는 TTL 까지 남는다 — 막는 게 아니라 무효화 실패를 운영에 표면화하는 것.
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
                log.error("캐시 evict 실패 → 전파 cache={} key={}", cache.getName(), key, exception);
                throw exception;
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("캐시 clear 실패 → 전파 cache={}", cache.getName(), exception);
                throw exception;
            }
        };
    }
}
