package com.groove.auth.jwt;

import com.groove.member.entity.MemberRole;

/** Access Token 파싱 결과. issuedAt 은 초 단위 epoch 시각이다. */
public record TokenClaims(Long memberId, MemberRole role, long issuedAt) {
}
