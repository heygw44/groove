package com.groove.security;

/**
 * Spring Security Authentication.principal 에 주입되는 인증 주체. memberId, role 을 보관한다.
 *
 * 도메인 무관 보안 1차 타입이라 중립 슬라이스(com.groove.security)에 둔다 — 모든 표현 계층이 의존하되
 * 어느 도메인도 역참조하지 않게 한다(슬라이스 순환 방지). role 은 member 도메인 enum 대신
 * 문자열로 보관해 member 의존을 끊는다.
 */
public record AuthPrincipal(Long memberId, String role) {
}
