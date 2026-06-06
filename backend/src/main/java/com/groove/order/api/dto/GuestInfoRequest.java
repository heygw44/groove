package com.groove.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "게스트 이메일 (주문 조회 시 본인 확인에 사용)", example = "guest@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @Schema(description = "게스트 연락처 (숫자만 10~11자)", example = "01012345678",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자만 10~11자여야 합니다")
        String phone
) {
}
