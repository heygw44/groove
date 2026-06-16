package com.groove.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청.
 *
 * <p>newPassword 는 비밀번호 정책(최소 10자 + 영·숫·특수 각 1자 이상)을 적용한다.
 * currentPassword 는 존재 여부(@NotBlank)만 검증한다.
 *
 * <p>current == new 거부는 @AssertTrue 파생 메서드로 처리한다. 위반 시 400.
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
     * 신규 비밀번호가 현재 비밀번호와 동일하면 거부한다. 한쪽이 비어 있으면 통과시킨다.
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
