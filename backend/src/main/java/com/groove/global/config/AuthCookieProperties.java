package com.groove.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Refresh 쿠키의 Secure 속성. 로컬 HTTP 는 false, 운영 HTTPS 는 true. */
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(boolean secure) {
}
