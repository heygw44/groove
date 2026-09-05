package com.groove.auth.jwt;

import com.groove.member.entity.MemberRole;

/** Access Token 파싱 결과. */
public record TokenClaims(Long memberId, MemberRole role) {
}
