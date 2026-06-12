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
 * 요청별로 적용할 RateLimitPolicy 를 선택하고 정책당 토큰 버킷을 관리한다. 등록된 정책은 @Order(또는 Ordered)에 따라
 * 정렬되며 가장 먼저 매치되는 정책이 적용된다. 정책 이름은 부팅 시점에 중복 여부를 검증한다.
 *
 * 수평 확장 제약 (#164): 버킷은 인스턴스 로컬(인메모리) Caffeine 캐시(buckets)에 저장되므로 단일 인스턴스 배포를
 * 전제한다(ADR-13). 로드밸런서 뒤에 N대를 두고 수평 확장하면 인스턴스마다 독립 버킷을 유지해 동일 IP/회원의 실효 한도가
 * N배가 되고, 무차별 대입·계정 열거·쿠폰 사재기 억제력이 인스턴스 수에 비례해 약화된다. 수평 확장 시에는
 * bucket4j-redis(+Lettuce) 분산 버킷으로 교체하거나 게이트웨이/WAF 계층 rate limit 으로 이관해야 한다.
 * (ARCHITECTURE.md §10.5·§11.1·§11.3 참조)
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
