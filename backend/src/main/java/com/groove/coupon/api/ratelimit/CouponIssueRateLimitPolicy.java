package com.groove.coupon.api.ratelimit;

import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.function.Supplier;

/**
 * POST /api/v1/coupons/{id}/issue 에 대한 회원 단위 Rate Limit 정책("회원당 분당 N회").
 * RateLimitFilter 는 Spring Security 필터보다 먼저 실행돼 SecurityContext 가 비어 있으므로, principal 대신
 * Authorization Bearer 토큰을 JwtProvider 로 직접 디코드해 memberId 를 키로 삼는다(부재/위조 시 IP 폴백).
 * 한도/리필 주기는 CouponRateLimitProperties 주입, 초과 시 RateLimitFilter 가 429 + Retry-After 작성.
 */
@Component
public class CouponIssueRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "coupon-issue";
    private static final String PATH_PATTERN = "/api/v1/coupons/*/issue";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final CouponRateLimitProperties.Policy config;
    private final JwtProvider jwtProvider;

    public CouponIssueRateLimitPolicy(CouponRateLimitProperties properties, JwtProvider jwtProvider) {
        this.config = properties.issue();
        this.jwtProvider = jwtProvider;
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
        return this::resolveMemberKey;
    }

    /** 발급 요청자의 memberId 를 키로 반환한다. 토큰 부재/위조 시 IP 폴백. */
    private String resolveMemberKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    return "member:" + jwtProvider.parseAccessToken(token).memberId();
                } catch (AuthException ignored) {
                    // 위조/만료 토큰 — IP 폴백.
                }
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
