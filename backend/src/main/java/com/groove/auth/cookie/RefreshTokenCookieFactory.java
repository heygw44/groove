package com.groove.auth.cookie;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.groove.global.config.AuthCookieProperties;
import com.groove.global.config.JwtProperties;

import lombok.RequiredArgsConstructor;

/** Refresh Token 쿠키를 만든다. Path 를 /api/v1/auth 로 제한해 다른 API 요청에는 쿠키가 실리지 않게 한다. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

	public static final String COOKIE_NAME = "refreshToken";

	private static final String PATH = "/api/v1/auth";
	private static final String SAME_SITE = "Lax";

	private final JwtProperties jwtProperties;
	private final AuthCookieProperties cookieProperties;

	public ResponseCookie create(String refreshToken) {
		return build(refreshToken, jwtProperties.refreshTokenExpiry());
	}

	public ResponseCookie expire() {
		return build("", Duration.ZERO);
	}

	private ResponseCookie build(String value, Duration maxAge) {
		return ResponseCookie.from(COOKIE_NAME, value)
				.httpOnly(true)
				.secure(cookieProperties.secure())
				.sameSite(SAME_SITE)
				.path(PATH)
				.maxAge(maxAge)
				.build();
	}
}
