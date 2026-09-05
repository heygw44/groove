package com.groove.payment.service;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentConfirmResult;
import com.groove.payment.dto.PaymentConfirmRequest;
import com.groove.payment.dto.PaymentConfirmResponse;

import lombok.RequiredArgsConstructor;

/**
 * 결제 승인 진입점. 토스 API 호출을 DB 트랜잭션 밖에서 하기 위해 이 클래스는 트랜잭션을 걸지 않는다.
 * 승인 실패를 {@code Payment.fail()} 로 저장하면서 예외도 그대로 던져야 하는데, 같은 트랜잭션 안에 두면
 * 실패 기록까지 롤백되기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

	private final PaymentConfirmWriter writer;
	private final PaymentClient paymentClient;

	public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
		ConfirmPreparation preparation = writer.prepare(memberId, request);
		if (preparation.alreadyApproved().isPresent()) {
			return preparation.alreadyApproved().get();
		}

		try {
			PaymentConfirmResult result = paymentClient.confirm(request.paymentKey(), preparation.orderNumber(),
					preparation.finalAmount());
			return writer.approve(preparation.paymentId(), request.paymentKey(), result);
		} catch (BusinessException e) {
			writer.fail(preparation.paymentId(), e.getMessage());
			throw e;
		}
	}
}
