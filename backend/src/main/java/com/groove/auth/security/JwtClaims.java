package com.groove.auth.security;

import com.groove.member.domain.MemberRole;

/**
 * 파싱된 JWT 클레임의 도메인 표현. Refresh Token 을 파싱하면 role 은 null 이고,
 * Access Token 만 권한 정보를 포함한다.
 */
public record JwtClaims(Long memberId, MemberRole role) {
}
