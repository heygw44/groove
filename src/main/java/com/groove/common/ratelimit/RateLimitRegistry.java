package com.groove.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
        this.policies = List.copyOf(policies);
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

    public record MatchedBucket(RateLimitPolicy policy, String key, Bucket bucket) {
    }
}
