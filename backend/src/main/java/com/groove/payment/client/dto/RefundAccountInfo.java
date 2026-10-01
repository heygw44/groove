package com.groove.payment.client.dto;

/** 가상계좌 결제 환불계좌. 토스 취소 API 의 refundReceiveAccount 로 전달한다. */
public record RefundAccountInfo(
		String bankCode,
		String accountNumber,
		String holderName
) {

	private static final int VISIBLE_TAIL_LENGTH = 4;

	/** 뒤 4자리만 남긴다. 로그·예외 메시지에 record 가 찍혀도 계좌번호 원문이 남지 않게 한다. */
	public static String maskAccountNumber(String accountNumber) {
		if (accountNumber == null) {
			return null;
		}
		int maskedLength = Math.max(accountNumber.length() - VISIBLE_TAIL_LENGTH, 0);
		if (maskedLength == 0) {
			return "*".repeat(accountNumber.length());
		}
		return "*".repeat(maskedLength) + accountNumber.substring(maskedLength);
	}

	@Override
	public String toString() {
		return "RefundAccountInfo[bankCode=" + bankCode + ", accountNumber=" + maskAccountNumber(accountNumber)
				+ ", holderName=" + holderName + "]";
	}
}
