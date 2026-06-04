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
 * {@code PATCH /api/v1/members/me/password} 에 대한 IP 기반 Rate Limit 정책 (#81).
 *
 * <p>인증된 엔드포인트지만 {@link com.groove.common.ratelimit.RateLimitFilter} 가 Spring Security
 * 필터보다 먼저 실행되어 principal 이 아직 없으므로, 로그인/회원가입과 동일하게 IP 를 키로 쓴다.
 * 탈취된 액세스 토큰으로 "현재 비밀번호" 를 브루트포스하는 시도를 IP 단위로 억제한다.
 *
 * <p>한도/리필 주기는 {@link AuthRateLimitProperties} 에서 주입받는다.
 * 한도 초과 시 {@code RateLimitFilter} 가 429 + {@code Retry-After} 응답을 작성한다.
 */
@Component
public class PasswordChangeRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "auth-password-change";
    static final String PATH = "/api/v1/members/me/password";

    private final AuthRateLimitProperties.Policy config;

    public PasswordChangeRateLimitPolicy(AuthRateLimitProperties properties) {
        this.config = properties.passwordChange();
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
