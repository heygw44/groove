package com.groove.auth.security;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급·파싱·검증을 담당하는 도메인 컴포넌트.
 *
 * <p>HMAC-SHA256(HS256) 으로 서명하고, {@code typ} 클레임으로 access/refresh 를 구분한다.
 * 파싱 실패는 외부 응답에서 토큰 종류를 노출하지 않기 위해 {@link AuthException} 으로 일원화한다
 * (만료만 {@code AUTH_EXPIRED_TOKEN}, 그 외 형식/위조/typ 불일치는 {@code AUTH_INVALID_TOKEN}).
 *
 * <p>{@link Clock} 을 주입받아 테스트에서 시간을 고정할 수 있게 했다.
 * 운영에서는 {@code Clock.systemUTC()} 빈을 주입한다.
 */
@Component
public class JwtProvider {

    private static final long CLOCK_SKEW_SECONDS = 30L;
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public JwtProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(Long memberId, MemberRole role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.accessTokenTtlSeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String issueRefreshToken(Long memberId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.refreshTokenTtlSeconds())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public JwtClaims parseAccessToken(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_ACCESS);
        MemberRole role = parseRole(claims);
        return new JwtClaims(parseMemberId(claims), role);
    }

    public JwtClaims parseRefreshToken(String token) {
        Claims claims = parse(token);
        requireType(claims, TYPE_REFRESH);
        return new JwtClaims(parseMemberId(claims), null);
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new AuthException(ErrorCode.AUTH_EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private void requireType(Claims claims, String expected) {
        String actual = claims.get(CLAIM_TYPE, String.class);
        if (!expected.equals(actual)) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private Long parseMemberId(Claims claims) {
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    private MemberRole parseRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        if (role == null) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        try {
            return MemberRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
    }
}
