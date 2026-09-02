package com.groove.auth;

import com.groove.member.entity.MemberRole;

/** 인증 컨텍스트의 principal 이자 {@code @AuthMember} 주입 타입. DB 조회 없이 토큰 클레임으로만 구성한다. */
public record LoginMember(Long id, MemberRole role) {
}
