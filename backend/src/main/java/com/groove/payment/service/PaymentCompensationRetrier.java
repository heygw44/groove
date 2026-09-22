package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.dto.PaymentCompensationCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * payment_compensation 대기 큐 한 건을 회수한다. 대사 스케줄러의 주기 회수와 웹훅이 트리거하는 즉시 회수가
 * 같은 로직을 쓰도록 스케줄러에서 뽑아냈다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationRetrier {

	private final PaymentClient paymentClient;
	private final PaymentCompensationWriter compensationWriter;
	private final Clock clock;

	/**
	 * 토스가 이미 취소된 결제로 응답하면(ALREADY_CANCELED_PAYMENT) PaymentClient 구현체가 이를 취소 성공으로
	 * 흡수해 그대로 완료 처리된다. 그 밖의 명확한 거절은 재시도해도 결과가 바뀌지 않으므로 상한을 기다리지 않고
	 * 즉시 수동 확인으로 넘긴다. 결과 불명은 재시도 횟수만 올린다.
	 */
	public void retry(PaymentCompensationCandidate candidate) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(candidate.paymentKey(), candidate.reason());
		} catch (BusinessException ex) {
			if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				compensationWriter.fail(candidate.paymentKey(), ex.getMessage());
				return;
			}
			log.error("결제 보상 대기 거절, 수동 확인 필요: paymentKey={}", candidate.paymentKey(), ex);
			compensationWriter.reviewManually(candidate.paymentKey(), ex.getMessage());
			return;
		} catch (RuntimeException ex) {
			log.warn("결제 보상 대기 회수 결과 불명 paymentKey={}", candidate.paymentKey(), ex);
			compensationWriter.fail(candidate.paymentKey(), ex.getMessage());
			return;
		}
		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		compensationWriter.complete(candidate.paymentKey(), canceledAt);
	}
}
