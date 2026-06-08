package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 쿠키 속성 설정 (#163).
 *
 * <p>refresh 토큰은 응답 body 가 아닌 {@code HttpOnly; Secure; SameSite} 쿠키로 내려가 JS 접근을
 * 차단한다. 쿠키 이름은 {@code @CookieValue} 에서 컴파일 상수가 필요해 프로퍼티가 아닌
 * {@link RefreshTokenCookieFactory#COOKIE_NAME} 으로 고정한다.
 *
 * <p>{@code secure} 는 운영(HTTPS)에서 반드시 true 여야 하나, 로컬 개발은 HTTP(localhost) 라
 * 쿠키 저장이 막히지 않도록 profile 별로 false 로 override 한다(application-local.yaml).
 *
 * <p>{@code sameSite=Strict} 면 cross-site 요청에 쿠키가 실리지 않아 refresh/logout 엔드포인트의
 * CSRF 가 차단된다. {@code path} 를 {@code /api/v1/auth} 로 좁혀 일반 API 호출엔 쿠키가 붙지 않는다.
 */
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshCookieProperties(
        Boolean secure,
        String sameSite,
        String path
) {

    private static final String DEFAULT_SAME_SITE = "Strict";
    private static final String DEFAULT_PATH = "/api/v1/auth";

    public RefreshCookieProperties {
        secure = secure != null ? secure : Boolean.TRUE;
        sameSite = (sameSite != null && !sameSite.isBlank()) ? sameSite : DEFAULT_SAME_SITE;
        path = (path != null && !path.isBlank()) ? path : DEFAULT_PATH;
    }
}
