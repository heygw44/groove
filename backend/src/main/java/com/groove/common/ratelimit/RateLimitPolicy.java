package com.groove.common.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.function.Supplier;

public interface RateLimitPolicy {

    String name();

    boolean appliesTo(HttpServletRequest request);

    Supplier<BucketConfiguration> bucketFactory();

    RateLimitKeyResolver keyResolver();

    /**
     * 분산 저장소(Redis) 원격 장애로 토큰 소비가 실패했을 때의 처리. true 면 한도 미적용으로 통과(fail-open,
     * 가용성 우선), false 면 429 차단(fail-closed, 사재기/남용 억제 우선). 기본은 fail-open.
     */
    default boolean failOpen() {
        return true;
    }

    /** 분당 capacity 토큰을 greedy 리필하는 단일 한도 BucketConfiguration 공급자. 전 정책이 같은 형태라 공용화한다. */
    static Supplier<BucketConfiguration> greedyBucket(long capacity, Duration refillPeriod) {
        return () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, refillPeriod))
                .build();
    }
}
