package com.groove.order.service;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.entity.PaymentStatus;

public record OrderCancelTarget(OrderStatus status, PaymentStatus paymentStatus) {

	/**
	 * PARTIAL_CANCELED 도 포함한다 - 상품 하나를 먼저 즉시 취소해 결제가 부분취소된 뒤에도 나머지 상품은
	 * 결제 있는 취소 경로(상품 단위 클레임)로 처리해야 한다.
	 */
	public boolean requiresPaymentCancel() {
		return paymentStatus == PaymentStatus.DONE || paymentStatus == PaymentStatus.CANCEL_REQUESTED
				|| paymentStatus == PaymentStatus.PARTIAL_CANCELED;
	}

	public boolean isWaitingForDeposit() {
		return paymentStatus == PaymentStatus.WAITING_FOR_DEPOSIT;
	}
}
