package com.groove.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 (API §3.2).
 *
 * <p>비밀번호 정책 검증은 가입 시점에 이미 통과했으므로 여기서는 형식만 검증한다.
 * 빈 값은 즉시 400 으로 차단해 인증 로직까지 도달하지 못하게 한다.
 */
public record LoginRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(max = 72)
        String password
) {
}
