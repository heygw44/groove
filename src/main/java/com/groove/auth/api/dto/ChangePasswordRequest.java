package com.groove.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 (#77, API.md §3.2 — PATCH /members/me/password).
 *
 * <p>{@code newPassword} 는 {@code SignupRequest} 와 동일한 비밀번호 정책(최소 10자 + 영·숫·특수
 * 각 1자 이상)을 적용한다. {@code currentPassword} 는 기존 저장값과 BCrypt 비교만 하므로 형식 검증
 * 없이 존재 여부({@code @NotBlank})만 본다.
 *
 * <p>{@code current == new} 거부는 스크립트 엔진 의존성이 필요한 {@code @ScriptAssert} 대신
 * 의존성 없는 {@code @AssertTrue} 파생 메서드로 처리한다. 위반 시 400 (VALIDATION_FAILED).
 */
public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 10, max = 72, message = "비밀번호는 10~72자여야 합니다 (BCrypt 72-byte 한계)")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다"
        )
        String newPassword
) {

    /**
     * 신규 비밀번호가 현재 비밀번호와 동일하면 거부한다. 한쪽이 null 이면 각 필드의
     * {@code @NotBlank} 가 먼저 보고하도록 통과시킨다(중복 위반 방지).
     */
    @AssertTrue(message = "새 비밀번호는 현재 비밀번호와 달라야 합니다")
    public boolean isNewPasswordDistinct() {
        if (currentPassword == null || newPassword == null) {
            return true;
        }
        return !currentPassword.equals(newPassword);
    }
}
