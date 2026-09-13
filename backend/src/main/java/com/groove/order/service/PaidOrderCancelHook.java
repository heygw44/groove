package com.groove.order.service;

/** 결제 완료 주문 취소를 결제 도메인에 맡긴다. 구현체와 호출자는 토스 호출을 위해 트랜잭션 밖에서 실행한다. */
public interface PaidOrderCancelHook {

	PaidOrderCancelResult cancel(Long orderId, Long memberId, String reason);
}
