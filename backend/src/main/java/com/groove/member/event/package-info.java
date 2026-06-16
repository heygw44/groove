/**
 * 회원 도메인 이벤트. MemberWithdrawnEvent — 회원 탈퇴 완료 시 발행, cart·auth 모듈이 AFTER_COMMIT 리스너로
 * 자기 데이터(장바구니 / 리프레시 토큰)만 정리한다.
 */
package com.groove.member.event;
