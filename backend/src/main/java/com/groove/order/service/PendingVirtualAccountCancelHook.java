package com.groove.order.service;

/**
 * 입금 전 가상계좌 주문 취소를 결제 도메인에 맡긴다. 구현체는 토스 계좌 폐쇄를 위해 트랜잭션 밖에서 실행한다.
 * 반환값은 복원된 한정반 드롭 id(없으면 null)다.
 */
public interface PendingVirtualAccountCancelHook {

	Long cancel(Long orderId, Long memberId, String reason);
}
