package com.groove.order.service;

import com.groove.payment.client.dto.RefundAccountInfo;

/** 결제 완료 주문 취소를 결제 도메인에 맡긴다. 구현체와 호출자는 토스 호출을 위해 트랜잭션 밖에서 실행한다. */
public interface PaidOrderCancelHook {

	default PaidOrderCancelResult cancel(Long orderId, Long memberId, String reason) {
		return cancel(orderId, memberId, reason, null);
	}

	/** refundAccount 는 가상계좌로 결제된 주문을 취소할 때만 필요하다. */
	PaidOrderCancelResult cancel(Long orderId, Long memberId, String reason, RefundAccountInfo refundAccount);
}
