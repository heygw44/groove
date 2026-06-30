package com.groove.auth.security.ratelimit;

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

import java.util.function.Supplier;

/**
 * PATCH /api/v1/members/me/password 에 대한 회원 단위 Rate Limit 정책.
 *
 * RateLimitFilter 는 Spring Security 필터보다 먼저 실행되어 SecurityContext 가 비어 있으므로,
 * Authorization 헤더의 Bearer 토큰을 JwtProvider 로 직접 디코드해 memberId 를 키로 삼는다.
 * 토큰이 없거나 위조면 IP 로 폴백한다.
 *
 * 한도/리필 주기는 AuthRateLimitProperties 에서 주입받는다. 한도 초과 시 RateLimitFilter 가 429 +
 * Retry-After 응답을 작성한다.
 */
@Component
public class PasswordChangeRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "auth-password-change";
    static final String PATH = "/api/v1/members/me/password";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthRateLimitProperties.Policy config;
    private final JwtProvider jwtProvider;

    public PasswordChangeRateLimitPolicy(AuthRateLimitProperties properties, JwtProvider jwtProvider) {
        this.config = properties.passwordChange();
        this.jwtProvider = jwtProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return HttpMethod.PATCH.matches(request.getMethod())
                && PATH.equals(RequestPaths.normalizedPath(request));
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
     * 비밀번호 변경 요청자의 memberId 를 키로 반환한다. 토큰 부재/위조 시 IP 로 폴백한다.
     */
    private String resolveMemberKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    return "member:" + jwtProvider.parseAccessToken(token).memberId();
                } catch (AuthException ignored) {
                    // 위조/만료 토큰 — IP 폴백으로 떨어진다.
                }
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
