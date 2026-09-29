package com.groove.payment.client.dto;

import java.math.BigDecimal;

/**
 * 토스 취소 요청 바디. cancelAmount 가 null 이면 전액취소(필드 자체를 생략, {@code @JsonInclude(NON_NULL)}은
 * {@code TossProperties} 쪽 RestClient 설정을 따른다). refundReceiveAccount 는 입금된 가상계좌를 환불할 때만 채운다.
 */
public record TossCancelRequest(String cancelReason, BigDecimal cancelAmount,
		RefundReceiveAccount refundReceiveAccount) {

	public static TossCancelRequest of(PaymentCancelCommand command) {
		RefundAccountInfo refundAccount = command.refundReceiveAccount();
		RefundReceiveAccount account = refundAccount == null ? null
				: new RefundReceiveAccount(refundAccount.bankCode(), refundAccount.accountNumber(),
						refundAccount.holderName());
		return new TossCancelRequest(command.reason(), command.cancelAmount(), account);
	}

	public record RefundReceiveAccount(String bank, String accountNumber, String holderName) {
	}
}
