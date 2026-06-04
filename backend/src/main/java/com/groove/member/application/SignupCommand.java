package com.groove.member.application;

/**
 * 회원가입 도메인 입력 (API DTO 와 분리).
 *
 * <p>비밀번호는 평문(검증 통과 직후 한 번만). 서비스 내부에서 즉시 해시되어 엔티티에 저장된다.
 */
public record SignupCommand(
        String email,
        String password,
        String name,
        String phone
) {
}
