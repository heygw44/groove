package com.groove.payment.api.ratelimit;

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

import java.util.Set;
import java.util.function.Supplier;

/**
 * 결제 생성(checkout) POST 회원 단위 Rate Limit 정책(회원당 분당 5회).
 * 대상은 POST /api/v1/payments 와 /toss/checkout. 토스 웹훅은 회원 토큰이 없고 토스 IP 로 몰려 이 한도에 throttle 되므로
 * {@link PaymentWebhookRateLimitPolicy}(IP 키잉)로 분리한다.
 * 키잉은 회원이면 Bearer 토큰을 직접 디코드한 memberId, 토큰 없음/위조면 IP 폴백.
 */
@Component
public class PaymentRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "payment-create";
    // 웹훅은 별도 정책으로 분리한다.
    static final Set<String> CREATE_PATHS = Set.of("/api/v1/payments", "/api/v1/payments/toss/checkout");
    private static final String BEARER_PREFIX = "Bearer ";

    private final PaymentRateLimitProperties.Policy config;
    private final JwtProvider jwtProvider;

    public PaymentRateLimitPolicy(PaymentRateLimitProperties properties, JwtProvider jwtProvider) {
        this.config = properties.post();
        this.jwtProvider = jwtProvider;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean appliesTo(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && CREATE_PATHS.contains(RequestPaths.normalizedPath(request));
    }

    @Override
    public Supplier<BucketConfiguration> bucketFactory() {
        return RateLimitPolicy.greedyBucket(config.capacity(), config.refillPeriod());
    }

    @Override
    public RateLimitKeyResolver keyResolver() {
        return this::resolveMemberKey;
    }

    /** 결제 요청자의 memberId 를 키로 반환한다. 게스트·위조 토큰이면 IP 폴백. */
    private String resolveMemberKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    return "member:" + jwtProvider.parseAccessToken(token).memberId();
                } catch (AuthException ignored) {
                    // 위조/만료 토큰은 IP 폴백.
                }
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}
