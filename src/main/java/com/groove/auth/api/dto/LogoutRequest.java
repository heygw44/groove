package com.groove.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그아웃 요청.
 *
 * <p>refresh 토큰을 받아 서버 측에서 폐기한다. 토큰이 잘못된 형식·만료·미존재 라도
 * RFC 7009 § 2.2 에 따라 응답은 항상 200 (토큰 유효성 누설 차단).
 */
public record LogoutRequest(
        @NotBlank
        String refreshToken
) {
}
