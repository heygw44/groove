package com.groove.auth.security.ratelimit;

import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * POST /api/v1/auth/signup 의 IP 기반 Rate Limit 정책. 한도/리필은 AuthRateLimitProperties 에서 주입받고,
 * 초과 시 RateLimitFilter 가 429 + Retry-After 를 작성한다.
 */
@Component
public class SignupRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "auth-signup";
    static final String PATH = "/api/v1/auth/signup";

    private final AuthRateLimitProperties.Policy config;

    public SignupRateLimitPolicy(AuthRateLimitProperties properties) {
        this.config = properties.signup();
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
    public Supplier<BucketConfiguration> bucketFactory() {
        return RateLimitPolicy.greedyBucket(config.capacity(), config.refillPeriod());
    }

    @Override
    public RateLimitKeyResolver keyResolver() {
        return RateLimitKeyResolver.clientIp();
    }
}
