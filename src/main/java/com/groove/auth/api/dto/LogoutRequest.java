package com.groove.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그아웃 요청.
 *
 * <p>본 이슈(#21) 범위에서는 토큰 형식 검증만 수행하고 200 을 반환한다.
 * 실제 무효화(revoke) 는 RefreshToken 영속화가 도입되는 #22 에서 추가된다.
 */
public record LogoutRequest(
        @NotBlank
        String refreshToken
) {
}
