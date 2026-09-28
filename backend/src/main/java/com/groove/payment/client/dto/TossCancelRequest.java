package com.groove.payment.client.dto;

/** 토스 취소 요청 바디. refundReceiveAccount 는 입금된 가상계좌를 환불할 때만 채운다. */
public record TossCancelRequest(String cancelReason, RefundReceiveAccount refundReceiveAccount) {

	public static TossCancelRequest of(String reason, RefundAccountInfo refundAccount) {
		RefundReceiveAccount account = refundAccount == null ? null
				: new RefundReceiveAccount(refundAccount.bankCode(), refundAccount.accountNumber(),
						refundAccount.holderName());
		return new TossCancelRequest(reason, account);
	}

	public record RefundReceiveAccount(String bank, String accountNumber, String holderName) {
	}
}
