package com.groove.auth.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 로그아웃 요청.
 *
 * <p>refresh 토큰을 받아 서버 측에서 폐기한다. 토큰이 잘못된 형식·만료·미존재 라도
 * RFC 7009 § 2.2 에 따라 응답은 항상 200 (토큰 유효성 누설 차단).
 *
 * <p>제약은 {@code @NotNull} 만 적용한다 — 필드 자체 누락은 RFC 6749 § 5.2
 * {@code invalid_request} 에 해당해 400 으로 응답하지만, 빈 문자열·공백은
 * "invalid token" 으로 보고 멱등 200 처리한다.
 */
public record LogoutRequest(
        @NotNull
        String refreshToken
) {
}
