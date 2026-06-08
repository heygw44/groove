package com.groove.auth.security.ratelimit;

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

import java.util.function.Supplier;

/**
 * {@code PATCH /api/v1/members/me/password} 에 대한 <b>회원 단위</b> Rate Limit 정책 (#81, #167).
 *
 * <p>인증된 엔드포인트이므로 "회원당 N회" 를 키로 쓴다. 단, {@link com.groove.common.ratelimit.RateLimitFilter}
 * 는 Spring Security 필터보다 먼저 실행되어 {@code SecurityContext} 가 아직 비어 있으므로 — principal 대신
 * Authorization 헤더의 Bearer 토큰을 {@link JwtProvider} 로 직접 디코드해 memberId 를 키로 삼는다
 * ({@link com.groove.coupon.api.ratelimit.CouponIssueRateLimitPolicy} 와 동일 패턴). 토큰이 없거나
 * 위조면(곧 Security 가 401 처리) IP 로 폴백해 키가 항상 결정된다.
 *
 * <p>IP 만 키로 쓰면 (1) NAT/CGNAT 뒤 정상 사용자들이 한도를 서로 소진하고, (2) 탈취한 access 토큰으로
 * "현재 비밀번호" 를 브루트포스할 때 프록시 로테이션으로 IP 만 바꾸면 계정 단위 누적 제한이 사라진다.
 * memberId 키잉으로 두 문제를 모두 차단한다.
 *
 * <p>한도/리필 주기는 {@link AuthRateLimitProperties} 에서 주입받는다.
 * 한도 초과 시 {@code RateLimitFilter} 가 429 + {@code Retry-After} 응답을 작성한다.
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
     * 비밀번호 변경 요청자의 memberId 를 키로 반환한다. 토큰 부재/위조 시 IP 폴백 — 그 요청들은 어차피
     * Security 에서 401 이지만, 키가 null 이 되지 않도록 결정적 폴백을 둔다.
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
        // 이 폴백은 어차피 곧 401 이 될 무/위조 토큰 요청에만 닿으므로 실질 영향은 작다.
        return "ip:" + request.getRemoteAddr();
    }
}
