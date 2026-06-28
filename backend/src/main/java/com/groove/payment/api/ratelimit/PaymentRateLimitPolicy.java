package com.groove.payment.api.ratelimit;

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

import java.util.Set;
import java.util.function.Supplier;

/**
 * 결제 생성(checkout) POST 회원 단위 Rate Limit 정책 (회원당 분당 5회).
 *
 * 대상은 POST /api/v1/payments 와 POST /api/v1/payments/toss/checkout 둘 다. 토스 웹훅(/toss/webhook)은
 * 회원 토큰이 없고 토스 IP 로 몰려 이 한도(5/분)에 throttle 되므로 제외하고 {@link PaymentWebhookRateLimitPolicy}(IP 키잉)로 분리한다.
 * 키잉은 회원이면 Bearer 토큰을 직접 디코드한 memberId, 토큰 없음/위조면 IP 폴백.
 * 한도/리필은 PaymentRateLimitProperties 주입, 초과 시 RateLimitFilter 가 429 + Retry-After.
 */
@Component
public class PaymentRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "payment-create";
    /** 회원 키잉 한도를 적용할 결제 생성 경로 — generic 요청 + 토스 checkout. 웹훅은 별도 정책으로 분리한다. */
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

    /**
     * 결제 요청자의 memberId 를 키로 반환한다. 게스트·위조 토큰이면 IP 폴백.
     */
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
        // getRemoteAddr() 를 클라이언트 IP 키로 사용한다.
        return "ip:" + request.getRemoteAddr();
    }
}
