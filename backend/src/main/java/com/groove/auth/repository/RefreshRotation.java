package com.groove.auth.repository;

/** refresh_rotate.lua 실행 결과. GRACE 는 새로 발급하지 않고 현재 토큰을 그대로 담는다. */
public record RefreshRotation(RotationResult result, String refreshToken) {
}
