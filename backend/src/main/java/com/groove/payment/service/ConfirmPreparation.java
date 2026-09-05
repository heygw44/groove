package com.groove.payment.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.groove.payment.dto.PaymentConfirmResponse;

/**
 * {@link PaymentConfirmWriter#prepare} 결과. {@code alreadyApproved} 가 있으면 이미 같은 paymentKey 로
 * 승인된 멱등 요청이라 토스를 호출하지 않고 그대로 반환한다.
 */
public record ConfirmPreparation(
		Long paymentId,
		Long orderId,
		String orderNumber,
		BigDecimal finalAmount,
		Optional<PaymentConfirmResponse> alreadyApproved
) {
}
