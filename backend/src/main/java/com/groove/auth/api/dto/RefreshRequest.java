package com.groove.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh Token 갱신 요청.
 *
 * <p>현재 refresh 토큰을 본문으로 받아 회전된 새 access·refresh 페어를 응답한다 (#22).
 */
public record RefreshRequest(
        @NotBlank
        String refreshToken
) {
}
