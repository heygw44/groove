package com.groove.auth.jwt;

/** Refresh Token 파싱 결과. sessionId 는 로그인 시 발급되어 Redis 세션 키를 구성한다. */
public record RefreshTokenClaims(Long memberId, String sessionId) {
}
