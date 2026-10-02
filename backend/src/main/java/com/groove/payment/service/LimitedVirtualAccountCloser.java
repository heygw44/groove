package com.groove.payment.service;

import org.springframework.stereotype.Component;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.dto.PaymentReconcileCandidate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대사·웹훅이 발견한 한정반 가상계좌를 닫는다. 토스 cancel(HTTP)은 트랜잭션 밖에서 부르고 결과 반영만
 * {@link PaymentReconcileService#recordLimitedVirtualAccountClose} 의 짧은 트랜잭션에 맡긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimitedVirtualAccountCloser {

	/** confirm 거절 경로(PaymentConfirmService)와 대사 폐쇄 경로가 같은 사유를 쓴다. */
	public static final String REASON = "한정반 가상계좌 결제 불가";

	private final PaymentClient paymentClient;
	private final PaymentReconcileService reconcileService;

	/** ALREADY_CANCELED_PAYMENT 는 TossPaymentClient 가 성공으로 흡수한다. */
	public void close(PaymentReconcileCandidate candidate, String paymentKey, String detail) {
		try {
			paymentClient.cancel(paymentKey, REASON);
		} catch (BusinessException ex) {
			reconcileService.recordLimitedVirtualAccountClose(candidate, ex, detail);
			return;
		} catch (RuntimeException ex) {
			log.warn("한정반 가상계좌 폐쇄 결과 불명 paymentId={}, orderId={}", candidate.paymentId(),
					candidate.orderId(), ex);
			reconcileService.recordLimitedVirtualAccountClose(candidate,
					new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN, ex.getMessage()), detail);
			return;
		}
		reconcileService.recordLimitedVirtualAccountClose(candidate, null, detail);
	}
}
