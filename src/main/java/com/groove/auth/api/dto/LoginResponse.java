package com.groove.auth.api.dto;

import com.groove.auth.application.TokenPair;

/**
 * 로그인 응답.
 *
 * <p>{@code tokenType} 은 RFC 6750 의 Bearer 고정. {@code expiresIn} 은 access 토큰의
 * 상대 TTL(초) 로, 클라이언트가 만료 직전 갱신을 트리거하는 데 사용한다.
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static LoginResponse from(TokenPair tokens) {
        return new LoginResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                "Bearer",
                tokens.accessTokenExpiresInSeconds()
        );
    }
}
