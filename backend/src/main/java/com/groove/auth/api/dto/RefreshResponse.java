package com.groove.auth.api.dto;

import com.groove.auth.application.TokenPair;

/**
 * Refresh 응답.
 *
 * <p>새 accessToken 만 body 에 싣는다. 회전된 새 refresh 토큰은 HttpOnly 쿠키로 내려간다.
 */
public record RefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static RefreshResponse from(TokenPair tokens) {
        return new RefreshResponse(
                tokens.accessToken(),
                TokenType.BEARER,
                tokens.accessTokenExpiresInSeconds()
        );
    }
}
