package com.groove.order.dto;

import com.groove.payment.client.dto.RefundAccountInfo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderCancelRequest(
		@Size(max = 200) String reason,
		@Valid RefundAccount refundAccount
) {

	public OrderCancelRequest(String reason) {
		this(reason, null);
	}

	/** 가상계좌로 결제된 주문을 취소할 때 토스가 요구하는 환불계좌. */
	public record RefundAccount(
			@NotBlank @Size(max = 10) String bankCode,
			@NotBlank @Size(max = 64) String accountNumber,
			@NotBlank @Size(max = 100) String holderName
	) {

		/** 요청에 계좌가 없으면 null. */
		public static RefundAccountInfo toInfo(RefundAccount refundAccount) {
			if (refundAccount == null) {
				return null;
			}
			return new RefundAccountInfo(refundAccount.bankCode(), refundAccount.accountNumber(),
					refundAccount.holderName());
		}
	}
}
