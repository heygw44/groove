package com.groove.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh Token 쿠키 속성(secure, sameSite, path) 설정. 쿠키 이름은
 * RefreshTokenCookieFactory#COOKIE_NAME 으로 고정한다.
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

        // SameSite 는 Strict/Lax/None 만 허용하고, 그 외 값이면 기동 시점에 거부한다.
        if (!sameSite.equalsIgnoreCase("Strict")
                && !sameSite.equalsIgnoreCase("Lax")
                && !sameSite.equalsIgnoreCase("None")) {
            throw new IllegalStateException(
                    "auth.refresh-cookie.same-site 는 Strict/Lax/None 중 하나여야 합니다: " + sameSite);
        }
        // SameSite=None 이면 Secure 가 필수이며, 위반 시 기동 실패.
        if (sameSite.equalsIgnoreCase("None") && !secure) {
            throw new IllegalStateException(
                    "SameSite=None refresh 쿠키는 Secure 가 필수입니다 (auth.refresh-cookie.secure=true 로 설정)");
        }
    }
}
