package com.groove.payment.client.dto;

import java.math.BigDecimal;

/**
 * 토스 취소 호출 인자. cancelAmount 가 null 이면 전액취소다.
 * idempotencyKey 는 취소 건마다 달라야 한다 - 고정 키({@code cancel-{paymentKey}})로 두 번째 부분취소를 보내면
 * 토스가 첫 번째 취소 결과를 그대로 재생한다.
 */
public record PaymentCancelCommand(
		String paymentKey,
		String reason,
		BigDecimal cancelAmount,
		String idempotencyKey,
		RefundAccountInfo refundReceiveAccount
) {

	/** 요청 행이 없는 전액취소 경로(보상 취소·입금 전 가상계좌 폐쇄) 전용 멱등키. 진행 중인 취소를 재시도할 때 같은 키를 다시 써야 한다. */
	private static final String LEGACY_IDEMPOTENCY_PREFIX = "cancel-";

	/**
	 * 전액취소, 기존 고정 멱등키({@code cancel-{paymentKey}}) 유지. {@link com.groove.payment.client.PaymentClient}의
	 * 2·3-arg cancel() 기본 메서드가 이 팩토리로 만든 커맨드를 넘긴다 - 보상·폐쇄 재시도가 첫 시도와 같은 키로
	 * 재호출해야 하기 때문이다.
	 */
	public static PaymentCancelCommand fullCancelLegacyKey(String paymentKey, String reason,
			RefundAccountInfo refundAccount) {
		return new PaymentCancelCommand(paymentKey, reason, null, LEGACY_IDEMPOTENCY_PREFIX + paymentKey,
				refundAccount);
	}

	/** 부분취소와 주문 전액취소. idempotencyKey 는 payment_cancel 행마다 새로 발급해 넘긴다. */
	public static PaymentCancelCommand of(String paymentKey, String reason, BigDecimal cancelAmount,
			String idempotencyKey, RefundAccountInfo refundAccount) {
		return new PaymentCancelCommand(paymentKey, reason, cancelAmount, idempotencyKey, refundAccount);
	}

	public boolean isFullCancel() {
		return cancelAmount == null;
	}
}
