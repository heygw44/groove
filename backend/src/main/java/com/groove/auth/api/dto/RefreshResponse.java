package com.groove.auth.api.dto;

import com.groove.auth.application.TokenPair;

/**
 * Refresh 응답.
 *
 * <p>응답 형식은 {@link LoginResponse} 와 동일하다 — access·refresh 둘 다 새 값이 실린다 (Rotation, DoD §1).
 */
public record RefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static RefreshResponse from(TokenPair tokens) {
        return new RefreshResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                TokenType.BEARER,
                tokens.accessTokenExpiresInSeconds()
        );
    }
}
