package com.groove.auth.dto;

/** 서비스가 발급한 토큰 묶음. refreshToken 은 쿠키로만 내려가고 바디에 노출하지 않는다. */
public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {
}
