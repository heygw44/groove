package com.groove.auth.security;

import com.groove.member.domain.MemberRole;

/**
 * Spring Security Authentication.principal 에 주입되는 인증 주체. memberId, role 을 보관한다.
 */
public record AuthPrincipal(Long memberId, MemberRole role) {
}
