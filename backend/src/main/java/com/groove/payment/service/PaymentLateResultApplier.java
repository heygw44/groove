package com.groove.payment.service;

import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;
import com.groove.payment.client.dto.PaymentLookupResult;
import com.groove.payment.dto.PaymentReconcileCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 뒤늦게 확인된 토스 조회 결과를 결제에 반영한다. 웹훅(PaymentWebhookService)과 정산 대사
 * (PaymentSettlementService)가 같은 판단·보상·취소 재시도 로직을 공유하기 위해 분리했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentLateResultApplier {

	private static final String RETRY_CANCEL_REASON = "주문 취소 재시도";

	private final PaymentReconcileService reconcileService;
	private final PaymentCompensator compensator;
	private final PaymentClient paymentClient;

	public PaymentReconcileOutcome apply(PaymentReconcileCandidate candidate, PaymentLookupResult lookup,
			String detail) {
		PaymentReconcileOutcome outcome = reconcileService.applyLate(candidate, lookup, detail);
		if (outcome.needsCompensation()) {
			CompensationResult result = compensator.cancelApproved(candidate.paymentId(), outcome.paymentKey(),
					outcome.approvedAt(), PaymentCompensator.ORDER_INVALIDATED_REASON);
			reconcileService.recordCompensation(candidate, result, detail);
		}
		if (outcome.needsCancelRetry()) {
			retryCancel(candidate, outcome.paymentKey());
		}
		return outcome;
	}

	private void retryCancel(PaymentReconcileCandidate candidate, String paymentKey) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(paymentKey, RETRY_CANCEL_REASON);
		} catch (BusinessException ex) {
			reconcileService.recordCancelRetry(candidate, null, ex);
			return;
		} catch (RuntimeException ex) {
			log.warn("취소 재시도 결과 불명 paymentId={}, orderId={}", candidate.paymentId(), candidate.orderId(), ex);
			reconcileService.recordCancelRetry(candidate, null,
					new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, ex.getMessage()));
			return;
		}
		reconcileService.recordCancelRetry(candidate, result, null);
	}
}
