package com.groove.support;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트 지원: 정책의 {@code Supplier<BucketConfiguration>} 를 인메모리 Caffeine ProxyManager 로 실제 Bucket 으로
 * 만든다. 프로덕션은 ProxyManager 추상화를 쓰므로 정책 단위 테스트도 같은 경로로 한도 거동을 검증한다.
 */
public final class RateLimitTestBuckets {

    private static final ProxyManager<String> SHARED = newProxyManager();
    private static final AtomicLong SEQ = new AtomicLong();

    private RateLimitTestBuckets() {
    }

    /** 매 호출마다 고유 키를 써 독립적인 Bucket 을 만든다. */
    public static Bucket from(BucketConfiguration configuration) {
        return SHARED.getProxy("test-" + SEQ.incrementAndGet(), () -> configuration);
    }

    /** 키 공유로 한도 누적을 검증하려는 테스트(Registry/Filter)용 새 ProxyManager. */
    public static ProxyManager<String> newProxyManager() {
        return Bucket4jCaffeine.<String>builderFor(Caffeine.newBuilder())
                .expirationAfterWrite(ExpirationAfterWriteStrategy.none())
                .build();
    }
}
