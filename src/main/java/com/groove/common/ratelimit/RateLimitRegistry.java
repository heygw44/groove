package com.groove.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 요청별로 적용할 {@link RateLimitPolicy}를 선택하고 정책당 토큰 버킷을 관리한다.
 * <p>등록된 정책은 {@code @Order} (또는 {@link org.springframework.core.Ordered})에 따라 정렬되며,
 * 가장 먼저 매치되는 정책이 적용된다. 정책 이름은 부팅 시점에 중복 여부를 검증한다.
 */
@Component
public class RateLimitRegistry {

    private static final long MAX_BUCKETS = 50_000L;
    private static final Duration BUCKET_TTL = Duration.ofHours(1);
    /** ASCII Unit Separator (0x1F) — 일반 정책명/키에는 등장하지 않으므로 충돌이 방지된다. */
    private static final char BUCKET_KEY_SEPARATOR = (char) 0x1F;

    private final List<RateLimitPolicy> policies;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterAccess(BUCKET_TTL)
            .build();

    public RateLimitRegistry(List<RateLimitPolicy> policies) {
        List<RateLimitPolicy> sorted = new ArrayList<>(policies);
        AnnotationAwareOrderComparator.sort(sorted);
        ensureUniqueNames(sorted);
        this.policies = List.copyOf(sorted);
    }

    public boolean isEmpty() {
        return policies.isEmpty();
    }

    public Optional<MatchedBucket> match(HttpServletRequest request) {
        for (RateLimitPolicy policy : policies) {
            if (policy.appliesTo(request)) {
                String key = policy.keyResolver().resolveKey(request);
                String bucketKey = policy.name() + BUCKET_KEY_SEPARATOR + key;
                Bucket bucket = buckets.get(bucketKey, ignored -> policy.bucketFactory().get());
                return Optional.of(new MatchedBucket(policy, key, bucket));
            }
        }
        return Optional.empty();
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
