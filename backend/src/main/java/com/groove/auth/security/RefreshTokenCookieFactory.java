package com.groove.auth.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh Token 을 담는 HttpOnly 쿠키를 생성·만료시키는 팩토리.
 * create(String) 로 회전된 refresh 토큰 쿠키를, clear() 로 Max-Age=0 삭제 쿠키를 만든다.
 * 쿠키 Max-Age 는 refresh 토큰 TTL 과 동일하다.
 */
@Component
public class RefreshTokenCookieFactory {

    /** Refresh Token 쿠키 이름. */
    public static final String COOKIE_NAME = "refreshToken";

    private final RefreshCookieProperties properties;
    private final Duration maxAge;

    public RefreshTokenCookieFactory(RefreshCookieProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.maxAge = Duration.ofSeconds(jwtProperties.refreshTokenTtlSeconds());
    }

    /** 회전된 refresh 토큰을 담은 영속 쿠키. TTL 은 refresh 토큰 만료와 동일. */
    public ResponseCookie create(String refreshToken) {
        return base(refreshToken, maxAge).build();
    }

    /** 로그아웃용 즉시 삭제 쿠키(Max-Age=0, 빈 값). */
    public ResponseCookie clear() {
        return base("", Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value, Duration ttl) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(ttl);
    }
}
