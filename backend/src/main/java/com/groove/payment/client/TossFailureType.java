package com.groove.payment.client;

import java.util.Set;

import org.springframework.http.HttpStatusCode;

/**
 * 토스 응답을 "명시적 거절"과 "결과 불명"으로 가른다.
 * 거절이면 결제를 FAILED 로 확정해도 되지만, 결과 불명이면 토스가 이미 처리했을 수 있어 조회로만 실제 상태를 확인해야 한다.
 */
public enum TossFailureType {

	/** 토스가 명확히 거절했다. 결제를 FAILED 로 확정해도 된다. */
	REJECTED,

	/** 토스가 처리했는지 알 수 없다. 조회로 실제 상태를 확인해야 한다. */
	RESULT_UNKNOWN,

	/** 이미 처리된 결제(같은 paymentKey 승인이 이미 끝남)라는 응답. confirm 에서는 조회로 실제 상태를 확인해 성공으로 잇는다. */
	ALREADY_PROCESSED;

	private static final String ALREADY_PROCESSED_PAYMENT_CODE = "ALREADY_PROCESSED_PAYMENT";

	/** 4xx 지만 토스가 실제로 처리했는지 단정할 수 없는 코드. https://docs.tosspayments.com/reference/error-codes */
	private static final Set<String> AMBIGUOUS_CLIENT_ERROR_CODES = Set.of(
			"PROVIDER_ERROR", "IDEMPOTENT_REQUEST_PROCESSING", "FORBIDDEN_CONSECUTIVE_REQUEST");

	public static TossFailureType classify(HttpStatusCode status, String tossCode) {
		if (ALREADY_PROCESSED_PAYMENT_CODE.equals(tossCode)) {
			return ALREADY_PROCESSED;
		}
		if (status.is5xxServerError()) {
			return RESULT_UNKNOWN;
		}
		if (tossCode == null) {
			return RESULT_UNKNOWN;
		}
		if (AMBIGUOUS_CLIENT_ERROR_CODES.contains(tossCode)) {
			return RESULT_UNKNOWN;
		}
		return REJECTED;
	}
}
