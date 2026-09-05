package com.groove.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 토스 결제창이 successUrl 로 돌려준 값을 그대로 받는다. amount 는 토스와 동일하게 원 단위 정수다. */
public record PaymentConfirmRequest(
		@NotBlank String paymentKey,
		@NotBlank String orderId,
		@NotNull @Positive Long amount
) {
}
