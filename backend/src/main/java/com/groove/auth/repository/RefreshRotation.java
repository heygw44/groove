package com.groove.auth.repository;

/**
 * refresh_rotate.lua 실행 결과. GRACE 는 새로 발급하지 않고 현재 토큰을 그대로 담는다.
 * absoluteExpiresAt 은 ROTATED·GRACE 에서만 채워지고, 그 외에는 0(해당 없음)이다.
 */
public record RefreshRotation(RotationResult result, String refreshToken, long absoluteExpiresAt) {
}
