package com.groove.payment.service;

import com.groove.payment.repository.PaymentCancelRepository;

/**
 * 결제 취소 요청마다 새로 발급하는 토스 멱등키({@code cancel-{paymentKey}-{seq}}). 순번은 한 결제 안에서
 * 부분·전액 구분 없이 이어진다. 같은 키로 다시 보내면 토스가 첫 응답을 재생하므로, 거절된 뒤의 재요청은
 * 반드시 새 키를 써야 한다.
 */
final class PaymentCancelIdempotencyKeys {

	static final String PREFIX = "cancel-";

	private PaymentCancelIdempotencyKeys() {
	}

	static String next(String paymentKey, Long paymentId, PaymentCancelRepository repository) {
		return PREFIX + paymentKey + "-" + (repository.countByPaymentId(paymentId) + 1);
	}
}
