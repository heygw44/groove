package com.groove.payment.api.ratelimit;

import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.ratelimit.RateLimitFilter;
import com.groove.common.ratelimit.RateLimitKeyResolver;
import com.groove.common.ratelimit.RateLimitPolicy;
import com.groove.common.ratelimit.RequestPaths;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code POST /api/v1/payments} 에 대한 <b>회원 단위</b> Rate Limit 정책 (#208, API.md §1.6 — 회원당 분당 5회).
 *
 * <p>결제 접수는 회원/게스트 공통(permitAll)이라 키잉은 회원이면 memberId, 게스트면 IP 다. 단,
 * {@link RateLimitFilter} 는 Spring Security 필터보다 먼저 실행되어 {@code SecurityContext} 가 아직
 * 비어 있으므로 — principal 대신 Authorization 헤더의 Bearer 토큰을 {@link JwtProvider} 로 직접 디코드해
 * memberId 를 키로 삼는다. 토큰이 없거나(게스트) 위조면 IP 로 폴백해 키가 항상 결정된다.
 *
 * <p>한도/리필 주기는 {@link PaymentRateLimitProperties} 에서 주입받는다. 초과 시 {@code RateLimitFilter}
 * 가 429 + {@code Retry-After} 를 작성한다.
 */
@Component
public class PaymentRateLimitPolicy implements RateLimitPolicy {

    static final String NAME = "payment-create";
    static final String PATH = "/api/v1/payments";
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
        return this::resolveMemberKey;
    }

    /**
     * 결제 요청자의 memberId 를 키로 반환한다. 게스트(토큰 부재)·위조 토큰이면 IP 폴백 — 키가 null 이
     * 되지 않도록 결정적 폴백을 둔다. 게스트 결제도 한 IP 에서의 폭주를 IP 버킷으로 억제한다.
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
        // 프록시 뒤 운영 시 getRemoteAddr() 가 실제 클라이언트 IP 가 되려면 컨테이너의
        // server.forward-headers-strategy 설정이 필요하다 (RateLimitKeyResolver.clientIp() 와 동일 전제).
        return "ip:" + request.getRemoteAddr();
    }
}
