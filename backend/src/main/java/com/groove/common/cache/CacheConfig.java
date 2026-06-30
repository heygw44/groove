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
     * read-side(get/put) 와 write-side(evict/clear) 를 분리한다.
     * - get 실패(Redis 단절·역직렬화 스큐 등)는 캐시 미스로 강등 → DB 폴백(가용성).
     * - put 실패는 무시 — 적재만 건너뛰므로 다음 미스에서 자가복구되고 stale 을 남기지 않는다.
     * - evict/clear 실패는 전파한다 — 쓰기 경로(create/update/delete/adjustStock)의 무효화 실패를 삼키면
     *   기존 albumDetail/albumLandingList 엔트리가 Redis 에 남아 TTL 만료 전까지 노드 간 stale 이 재발한다.
     *   호출자에게 표면화해 운영자가 감지하게 하고, 무효화되지 않은 stale 을 적중으로 재서빙하지 않는다.
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
                log.warn("캐시 put 실패 무시(다음 미스에서 재적재) cache={} key={}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // 무효화 실패는 stale 을 남기므로 삼키지 않고 전파한다(쓰기 경로 표면화).
                log.error("캐시 evict 실패 → 전파(stale 방지) cache={} key={}", cache.getName(), key, exception);
                throw exception;
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("캐시 clear 실패 → 전파(stale 방지) cache={}", cache.getName(), exception);
                throw exception;
            }
        };
    }
}
