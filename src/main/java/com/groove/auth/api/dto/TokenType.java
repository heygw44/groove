package com.groove.auth.api.dto;

/**
 * RFC 6750 Bearer 토큰 응답 타입 상수.
 *
 * <p>{@link LoginResponse}, {@link RefreshResponse} 등 토큰을 반환하는 응답 DTO 가
 * 동일 문자열을 하드코딩하지 않도록 단일 출처를 둔다.
 */
final class TokenType {

    static final String BEARER = "Bearer";

    private TokenType() {
    }
}
