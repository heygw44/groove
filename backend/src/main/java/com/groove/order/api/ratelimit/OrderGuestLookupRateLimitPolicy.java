package com.groove.order.api.ratelimit;

import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.function.Supplier;

/**
 * POST /api/v1/orders/{orderNumber}/guest-lookup 에 대한 IP 단위 Rate Limit 정책.
 * RateLimitKeyResolver.clientIp() 로 IP 키잉하고, 한도/리필 주기는 OrderRateLimitProperties 에서 주입받는다.
 */
@Component
public class OrderGuestLookupRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "order-guest-lookup";
    private static final String PATH_PATTERN = "/api/v1/orders/*/guest-lookup";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final OrderRateLimitProperties.Policy config;

    public OrderGuestLookupRateLimitPolicy(OrderRateLimitProperties properties) {
        this.config = properties.guestLookup();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && pathMatcher.match(PATH_PATTERN, RequestPaths.normalizedPath(request));
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
