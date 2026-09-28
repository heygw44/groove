package com.groove.order.service;

/**
 * {@link OrderExpirationWriter#checkExpirable} 결과. waitingForDeposit 이 아니면 이미 그 트랜잭션
 * 안에서 취소·복원까지 끝난 상태다. waitingForDeposit 이면 토스 계좌 폐쇄가 남아 있어 상태를 바꾸지 않았다.
 */
record OrderExpirationTarget(boolean waitingForDeposit, Long orderId, Long paymentId, String tossOrderId,
		String paymentKey) {

	static OrderExpirationTarget simple() {
		return new OrderExpirationTarget(false, null, null, null, null);
	}

	static OrderExpirationTarget virtualAccount(Long orderId, Long paymentId, String tossOrderId,
			String paymentKey) {
		return new OrderExpirationTarget(true, orderId, paymentId, tossOrderId, paymentKey);
	}
}
