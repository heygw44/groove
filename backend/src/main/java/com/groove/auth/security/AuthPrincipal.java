package com.groove.auth.security;

import com.groove.member.domain.MemberRole;

/**
 * Spring Security {@code Authentication.principal} 에 주입되는 인증 주체.
 *
 * <p>JWT 만으로 자족적인 정보(memberId, role)를 보관한다. 컨트롤러에서
 * {@code @AuthenticationPrincipal AuthPrincipal} 로 받아 사용한다.
 */
public record AuthPrincipal(Long memberId, MemberRole role) {
}
