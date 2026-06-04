package com.groove.member.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청 (#78, API.md §3.2 — DELETE /members/me). 본인 비밀번호 재확인.
 *
 * <p>{@code password} 는 저장된 해시와 BCrypt 비교만 하므로 형식(길이·복잡도) 검증 없이 존재
 * 여부({@code @NotBlank})만 본다 — {@code ChangePasswordRequest.currentPassword} 와 동일 정책.
 * 형식 검증을 두면 정책이 바뀌었을 때 기존 비밀번호로 탈퇴조차 못 하는 모순이 생긴다.
 */
public record WithdrawRequest(
        @NotBlank
        String password
) {
}
