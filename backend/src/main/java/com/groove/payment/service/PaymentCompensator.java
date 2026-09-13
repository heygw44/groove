package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.groove.global.common.BusinessException;
import com.groove.payment.client.PaymentClient;
import com.groove.payment.client.dto.PaymentCancelResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 토스가 승인한 결제를 뒤늦게 취소하는 보상 경로. 토스 호출을 트랜잭션 없이 하는 이유는 취소 실패가 이 결과를
 * 기록하는 DB 쓰기를 함께 롤백시키지 않게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensator {

	public static final String ORDER_INVALIDATED_REASON = "승인 후 주문 무효로 자동 취소";
	public static final String DUPLICATE_APPROVAL_REASON = "같은 주문 중복 승인 자동 취소";

	private final PaymentClient paymentClient;
	private final PaymentConfirmWriter writer;
	private final Clock clock;

	/** paymentId 가 null 이면 DB 결제 행이 없는 키(같은 주문의 두 번째 승인)라 토스 취소만 한다. */
	public CompensationResult cancelApproved(Long paymentId, String paymentKey, LocalDateTime approvedAt,
			String reason) {
		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(paymentKey, reason);
		} catch (BusinessException ex) {
			log.error("승인 후 보상 취소 실패: paymentId={}, paymentKey={}, message={}", paymentId, paymentKey,
					ex.getMessage(), ex);
			if (paymentId != null) {
				safeMarkUnknown(paymentId, "보상 취소 실패: " + ex.getMessage());
			}
			return CompensationResult.notCanceled(ex.getMessage());
		}

		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		if (paymentId != null) {
			try {
				writer.markCompensated(paymentId, paymentKey, approvedAt, canceledAt, reason);
			} catch (RuntimeException ex) {
				log.error("보상 취소는 성공했으나 결제 반영에 실패함: paymentId={}", paymentId, ex);
			}
		}
		return CompensationResult.canceled(canceledAt);
	}

	private void safeMarkUnknown(Long paymentId, String reason) {
		try {
			writer.markUnknown(paymentId, reason);
		} catch (ObjectOptimisticLockingFailureException | BusinessException ex) {
			log.error("보상 취소 실패 기록 중 예외 발생: paymentId={}", paymentId, ex);
		}
	}
}
