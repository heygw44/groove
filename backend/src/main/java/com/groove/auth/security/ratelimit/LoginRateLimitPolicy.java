package com.groove.auth.security.ratelimit;

import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code POST /api/v1/auth/login} 에 대한 IP 기반 Rate Limit 정책.
 *
 * <p>한도/리필 주기는 {@link AuthRateLimitProperties} 에서 주입받는다.
 * 한도 초과 시 {@link com.groove.common.ratelimit.RateLimitFilter} 가 429 + {@code Retry-After} 응답을 작성한다.
 */
@Component
public class LoginRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "auth-login";
    static final String PATH = "/api/v1/auth/login";

    private final AuthRateLimitProperties.Policy config;

    public LoginRateLimitPolicy(AuthRateLimitProperties properties) {
        this.config = properties.login();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && PATH.equals(RequestPaths.normalizedPath(request));
    }

    @Override
    public Supplier<Bucket> bucketFactory() {
        long capacity = config.capacity();
        return () -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, config.refillPeriod()))
                .build();
    }

    @Override
    public RateLimitKeyResolver keyResolver() {
        return RateLimitKeyResolver.clientIp();
    }
}
