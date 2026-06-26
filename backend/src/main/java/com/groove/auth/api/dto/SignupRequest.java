package com.groove.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 비밀번호=최소 10자 + 영·숫·특수 각 1자 이상, 전화번호=필수·숫자만 10~11자.
 */
public record SignupRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEX, message = PasswordPolicy.COMPLEXITY_MESSAGE)
        String password,

        @NotBlank
        @Size(min = 1, max = 50)
        String name,

        @NotBlank
        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자만 10~11자여야 합니다")
        String phone
) {
}
