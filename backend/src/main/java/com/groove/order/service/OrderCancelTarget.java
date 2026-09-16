package com.groove.order.service;

import com.groove.order.entity.OrderStatus;
import com.groove.payment.entity.PaymentStatus;

public record OrderCancelTarget(OrderStatus status, PaymentStatus paymentStatus) {

	public boolean requiresPaymentCancel() {
		return paymentStatus == PaymentStatus.DONE || paymentStatus == PaymentStatus.CANCEL_REQUESTED;
	}
}
