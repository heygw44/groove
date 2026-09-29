package com.groove.payment.service;

import java.math.BigDecimal;

public record PaymentRefundResult(
		PaymentRefundStatus status,
		Long paymentCancelId,
		BigDecimal canceledAmount
) {
}
