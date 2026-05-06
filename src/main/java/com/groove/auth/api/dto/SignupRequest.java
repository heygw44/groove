package com.groove.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 (API §3.1).
 *
 * <p>비밀번호: 최소 10자 + 영·숫·특수 각 1자 이상.
 * 전화번호: 필수, 숫자만 10~11자.
 */
public record SignupRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 10, max = 100)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다"
        )
        String password,

        @NotBlank
        @Size(min = 1, max = 50)
        String name,

        @NotBlank
        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자만 10~11자여야 합니다")
        String phone
) {
}
