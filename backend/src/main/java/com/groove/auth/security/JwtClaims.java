package com.groove.auth.security;

import com.groove.member.domain.MemberRole;

/**
 * 파싱된 JWT 클레임의 도메인 표현.
 *
 * <p>Refresh Token 을 파싱한 경우 {@code role} 은 {@code null} 이다.
 * Access Token 만 권한 정보를 포함한다.
 */
public record JwtClaims(Long memberId, MemberRole role) {
}
