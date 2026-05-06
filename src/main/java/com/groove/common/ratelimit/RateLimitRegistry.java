package com.groove.common.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitRegistry {

    private final List<RateLimitPolicy> policies;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

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
                String bucketKey = policy.name() + ":" + key;
                Bucket bucket = buckets.computeIfAbsent(bucketKey, ignored -> policy.bucketFactory().get());
                return Optional.of(new MatchedBucket(policy, key, bucket));
            }
        }
        return Optional.empty();
    }

    public record MatchedBucket(RateLimitPolicy policy, String key, Bucket bucket) {
    }
}
