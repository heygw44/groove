package com.groove.order.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 게스트 주문자 정보 (API.md §3.5).
 *
 * <p>phone 정규식은 {@code SignupRequest} 와 동일 — 숫자만 10~11자.
 */
public record GuestInfoRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자만 10~11자여야 합니다")
        String phone
) {
}
