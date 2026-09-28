package com.groove.payment.dto;

import com.groove.order.dto.OrderCancelRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequest(
		@NotBlank @Size(max = 200) String reason,
		@Valid OrderCancelRequest.RefundAccount refundAccount
) {

	public PaymentCancelRequest(String reason) {
		this(reason, null);
	}
}
