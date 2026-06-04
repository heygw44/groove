package com.groove.member.event;

/**
 * 회원 탈퇴 완료 이벤트 (#78).
 *
 * <p>{@code MemberService.withdraw} 의 soft delete 트랜잭션 안에서 발행된다. 각 모듈은
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 로 수신해 <em>자기 데이터만</em> 정리한다 —
 * cart 는 장바구니 삭제, auth 는 리프레시 토큰 revoke. {@code OrderPaidEvent} 와 동일한 발행/구독 패턴이며,
 * 탈퇴 트랜잭션이 롤백되면 리스너는 호출되지 않는다(미확정 탈퇴에 대한 정리가 새지 않음).
 *
 * @param memberId 탈퇴한 회원 식별자
 */
public record MemberWithdrawnEvent(Long memberId) {
}
