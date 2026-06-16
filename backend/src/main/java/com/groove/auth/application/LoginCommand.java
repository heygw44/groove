package com.groove.auth.application;

/**
 * 로그인 도메인 입력. password 는 평문이다.
 */
public record LoginCommand(String email, String password) {
}
