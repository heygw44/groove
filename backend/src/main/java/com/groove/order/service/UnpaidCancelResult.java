package com.groove.order.service;

public record UnpaidCancelResult(boolean needsPaymentCancel, Long limitedDropId) {

	public static UnpaidCancelResult paymentCancelRequired() {
		return new UnpaidCancelResult(true, null);
	}

	public static UnpaidCancelResult canceled(Long limitedDropId) {
		return new UnpaidCancelResult(false, limitedDropId);
	}
}
