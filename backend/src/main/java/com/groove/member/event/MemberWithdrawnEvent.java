package com.groove.member.event;

/**
 * 회원 탈퇴 완료 이벤트. MemberService.withdraw 의 soft delete 트랜잭션 안에서 발행되며, 각 모듈이
 * @TransactionalEventListener(AFTER_COMMIT) 로 수신해 자기 데이터만 정리한다(cart 장바구니, auth 리프레시 토큰).
 */
public record MemberWithdrawnEvent(Long memberId) {
}
