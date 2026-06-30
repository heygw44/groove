package com.groove.common.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 요청별로 적용할 RateLimitPolicy 를 선택하고 정책당 토큰 버킷을 관리한다. 등록된 정책은 @Order(또는 Ordered)에 따라
 * 정렬되며 가장 먼저 매치되는 정책이 적용된다. 정책 이름은 부팅 시점에 중복 여부를 검증한다. 버킷 저장소는
 * {@link ProxyManager} 로 추상화된다(단일 인스턴스/로컬/테스트=Caffeine, 다중 인스턴스=Redis 공유 — RateLimitStoreConfig).
 */
@Component
public class RateLimitRegistry {

    /** Redis 키 네임스페이스 + 스키마 버전(롤링 배포 간 격리). Caffeine 경로엔 무해. */
    private static final String KEY_PREFIX = "groove:rl:v1:";
    /** 정책명과 키를 잇는 구분자 — ASCII Unit Separator (0x1F). */
    private static final char BUCKET_KEY_SEPARATOR = (char) 0x1F;

    private final List<CompiledPolicy> policies;
    private final ProxyManager<String> proxyManager;

    public RateLimitRegistry(List<RateLimitPolicy> policies, ProxyManager<String> proxyManager) {
        List<RateLimitPolicy> sorted = new ArrayList<>(policies);
        AnnotationAwareOrderComparator.sort(sorted);
        ensureUniqueNames(sorted);
        this.policies = sorted.stream().map(CompiledPolicy::new).toList();
        this.proxyManager = proxyManager;
    }

    public boolean isEmpty() {
        return policies.isEmpty();
    }

    public Optional<MatchedBucket> match(HttpServletRequest request) {
        for (CompiledPolicy compiled : policies) {
            RateLimitPolicy policy = compiled.policy();
            if (policy.appliesTo(request)) {
                String key = policy.keyResolver().resolveKey(request);
                Bucket bucket = proxyManager.getProxy(compiled.keyPrefix() + key, compiled.configSupplier());
                return Optional.of(new MatchedBucket(policy, key, bucket));
            }
        }
        return Optional.empty();
    }

    /** 정책별로 키 프리픽스와 BucketConfiguration 공급자를 부팅 시 1회 산출해 둔다(매 요청 람다·문자열 재생성 회피). */
    private record CompiledPolicy(RateLimitPolicy policy, String keyPrefix, Supplier<BucketConfiguration> configSupplier) {
        CompiledPolicy(RateLimitPolicy policy) {
            this(policy, KEY_PREFIX + policy.name() + BUCKET_KEY_SEPARATOR, policy.bucketFactory());
        }
    }

    private static void ensureUniqueNames(List<RateLimitPolicy> policies) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (RateLimitPolicy policy : policies) {
            if (!seen.add(policy.name())) {
                duplicates.add(policy.name());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Duplicate RateLimitPolicy names: " + duplicates);
        }
    }

    public record MatchedBucket(RateLimitPolicy policy, String key, Bucket bucket) {
    }
}
