package com.groove.auth.api.dto;

import com.groove.auth.application.TokenPair;

/**
 * Refresh 응답.
 *
 * <p>응답 형식은 {@link LoginResponse} 와 동일하다 — 새 accessToken 만 body 에 싣는다.
 * 회전된 새 refresh 토큰은 body 가 아닌 HttpOnly 쿠키로 내려간다(Rotation, DoD §1; #163).
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
