/**
 * 회원 도메인 이벤트.
 *
 * <p>{@link com.groove.member.event.MemberWithdrawnEvent} — 회원 탈퇴 완료 시
 * {@code MemberService.withdraw} 가 발행(#78). cart·auth 모듈이 각자 AFTER_COMMIT 리스너로 수신해
 * 자기 데이터(장바구니 / 리프레시 토큰)만 정리한다 — {@code OrderPaidEvent} 와 동일 패턴.
 */
package com.groove.member.event;
