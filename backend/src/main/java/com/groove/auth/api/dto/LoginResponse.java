package com.groove.auth.api.dto;

import com.groove.auth.application.TokenPair;

/**
 * 로그인 응답.
 *
 * <p>tokenType 은 Bearer 고정. expiresIn 은 access 토큰의 상대 TTL(초).
 * refresh 토큰은 body 가 아닌 HttpOnly 쿠키로 내려간다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static LoginResponse from(TokenPair tokens) {
        return new LoginResponse(
                tokens.accessToken(),
                TokenType.BEARER,
                tokens.accessTokenExpiresInSeconds()
        );
    }
}
