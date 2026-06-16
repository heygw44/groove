package com.groove.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청.
 *
 * <p>형식만 검증한다. 빈 값은 400 으로 차단한다.
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
