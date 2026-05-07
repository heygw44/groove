package com.groove.auth.application;

/**
 * 로그인 도메인 입력 (API DTO 와 분리).
 *
 * <p>{@code password} 는 평문이며 서비스 내부에서 1회만 BCrypt 와 비교된 뒤 폐기된다.
 */
public record LoginCommand(String email, String password) {
}
