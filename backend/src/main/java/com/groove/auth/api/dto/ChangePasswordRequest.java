package com.groove.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "현재 비밀번호", example = "P@ssw0rd123!")
        @NotBlank
        String currentPassword,

        @Schema(description = "새 비밀번호 (최소 10자 + 영·숫·특수 각 1자 이상, 현재 비밀번호와 달라야 함)", example = "N3wP@ssw0rd!")
        @NotBlank
        @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH, message = PasswordPolicy.LENGTH_MESSAGE)
        @Pattern(regexp = PasswordPolicy.COMPLEXITY_REGEX, message = PasswordPolicy.COMPLEXITY_MESSAGE)
        String newPassword
) {

    /**
     * 신규 비밀번호가 현재 비밀번호와 동일하면 거부한다. 한쪽이 비어 있으면 각 필드의
     * {@code @NotBlank} 가 먼저 보고하도록 통과시킨다(빈 값일 때 중복 위반 메시지 방지).
     */
    @AssertTrue(message = "새 비밀번호는 현재 비밀번호와 달라야 합니다")
    public boolean isNewPasswordDistinct() {
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return true;
        }
        return !currentPassword.equals(newPassword);
    }
}
