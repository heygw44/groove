package com.groove.member.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청 (DELETE /members/me). 본인 비밀번호 재확인. password 는 형식 검증 없이 존재 여부(@NotBlank)만 본다.
 */
public record WithdrawRequest(
        @Schema(description = "본인 확인용 현재 비밀번호", example = "P@ssw0rd123!")
        @NotBlank
        String password
) {
}
