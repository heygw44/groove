package com.groove.coupon.api.ratelimit;

import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.BucketConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.function.Supplier;

/**
 * 쿠폰 발급 회원 단위 Rate Limit. RateLimitFilter 가 Security 필터보다 먼저 실행돼 SecurityContext 가 비어 있어서
 * principal 대신 Bearer 토큰을 직접 디코드해 memberId 를 키로 삼는다(부재/위조 시 IP 폴백).
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
    public Supplier<BucketConfiguration> bucketFactory() {
        return RateLimitPolicy.greedyBucket(config.capacity(), config.refillPeriod());
    }

    @Override
    public RateLimitKeyResolver keyResolver() {
        return this::resolveMemberKey;
    }

    /**
     * 쿠폰 발급은 fail-closed — Redis 장애 시에도 한정 수량 사재기를 막기 위해 429 로 차단한다(나머지 정책은 fail-open).
     */
    @Override
    public boolean failOpen() {
        return false;
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
