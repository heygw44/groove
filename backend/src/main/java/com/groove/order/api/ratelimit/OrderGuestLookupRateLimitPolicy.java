package com.groove.order.api.ratelimit;

import com.groove.common.ratelimit.RateLimitFilter;
import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.function.Supplier;

/**
 * {@code POST /api/v1/orders/{orderNumber}/guest-lookup} 에 대한 <b>IP 단위</b> Rate Limit 정책
 * (#208, API.md §3.5 — orderNumber+email 무차별 대입 방지).
 *
 * <p>게스트 조회는 비인증 공개 엔드포인트라 회원 키가 없다. orderNumber+email 페어 추측 공격은 한 IP 의
 * 총 시도량을 제한하는 것이 정석이므로(email 을 키에 넣으면 email 을 바꿔 우회 가능) login·signup 과
 * 동일하게 {@link RateLimitKeyResolver#clientIp()} 로 IP 키잉한다. {@link RateLimitFilter} 가 Security
 * 보다 먼저 실행되어도 IP 는 항상 결정되므로 폴백이 불필요하다.
 *
 * <p>한도/리필 주기는 {@link OrderRateLimitProperties} 에서 주입받는다. 초과 시 {@code RateLimitFilter}
 * 가 429 + {@code Retry-After} 를 작성한다.
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
