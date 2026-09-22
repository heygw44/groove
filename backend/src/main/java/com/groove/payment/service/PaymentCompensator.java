package com.groove.payment.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.groove.global.alert.Alert;
import com.groove.global.alert.AlertNotifier;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
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
	private final PaymentCompensationWriter compensationWriter;
	private final Clock clock;
	private final AlertNotifier alertNotifier;

	/**
	 * paymentId 가 있는 경로 전용이다. paymentId 가 없는 보상은 payment_compensation 행에 채울 orderId·
	 * tossOrderId 가 필요해 6-인자 오버로드를 써야 한다.
	 */
	public CompensationResult cancelApproved(Long paymentId, String paymentKey, LocalDateTime approvedAt,
			String reason) {
		Objects.requireNonNull(paymentId, "결제 행이 없는 보상은 orderId 가 필요하다 - orderId·tossOrderId 를 받는 오버로드를 쓸 것");
		return cancelApproved(paymentId, paymentKey, approvedAt, reason, null, null);
	}

	/**
	 * paymentId 가 null 인 경로는 uk_payment_order 때문에 결제 행을 만들 수 없다. 토스 취소를 부르기 전에
	 * payment_compensation 행을 먼저 커밋해, 취소 자체가 실패해도 대사 스케줄러가 이 행을 이어받게 한다.
	 */
	public CompensationResult cancelApproved(Long paymentId, String paymentKey, LocalDateTime approvedAt,
			String reason, Long orderId, String tossOrderId) {
		if (paymentId == null) {
			compensationWriter.enqueue(paymentKey, orderId, tossOrderId, approvedAt, reason);
		}

		PaymentCancelResult result;
		try {
			result = paymentClient.cancel(paymentKey, reason);
		} catch (BusinessException ex) {
			log.error("승인 후 보상 취소 실패: paymentId={}, paymentKey={}, message={}", paymentId, paymentKey,
					ex.getMessage(), ex);
			alertNotifier.notify(Alert.critical("payment.compensation-failed",
					"승인 후 보상 취소 실패: paymentId=" + paymentId + ", paymentKey=" + paymentKey + ", message="
							+ ex.getMessage(),
					paymentId != null ? "paymentId=" + paymentId : "paymentKey=" + paymentKey));
			String detail = "보상 취소 실패: " + ex.getMessage();
			if (paymentId != null) {
				safeMarkUnknown(paymentId, detail);
			} else if (ex.getErrorCode() == ErrorCode.PAYMENT_RESULT_UNKNOWN) {
				safeCompensationFail(paymentKey, detail);
			} else {
				// 결과 불명이 아닌 명확한 거절은 재시도해도 같은 결과라, 스케줄러가 다시 시도하지 않도록 여기서
				// 바로 수동 확인으로 넘긴다.
				safeCompensationReviewManually(paymentKey, detail);
			}
			return CompensationResult.notCanceled(ex.getMessage());
		}

		LocalDateTime canceledAt = result.canceledAt() != null ? result.canceledAt() : LocalDateTime.now(clock);
		if (paymentId != null) {
			try {
				writer.markCompensated(paymentId, paymentKey, approvedAt, canceledAt, reason);
			} catch (RuntimeException ex) {
				log.error("보상 취소는 성공했으나 결제 반영에 실패함: paymentId={}", paymentId, ex);
				alertNotifier.notify(Alert.critical("payment.compensation-failed",
						"보상 취소는 성공했으나 결제 반영에 실패함: paymentId=" + paymentId, "paymentId=" + paymentId));
			}
		} else {
			safeCompensationComplete(paymentKey, canceledAt);
		}
		return CompensationResult.canceled(canceledAt);
	}

	private void safeMarkUnknown(Long paymentId, String reason) {
		try {
			writer.markUnknown(paymentId, reason);
		} catch (ObjectOptimisticLockingFailureException | BusinessException ex) {
			log.error("보상 취소 실패 기록 중 예외 발생: paymentId={}", paymentId, ex);
			alertNotifier.notify(Alert.critical("payment.compensation-failed",
					"보상 취소 실패 기록 중 예외 발생: paymentId=" + paymentId, "paymentId=" + paymentId));
		}
	}

	private void safeCompensationFail(String paymentKey, String detail) {
		try {
			compensationWriter.fail(paymentKey, detail);
		} catch (RuntimeException ex) {
			log.error("보상 대기 실패 기록 중 예외 발생: paymentKey={}", paymentKey, ex);
			alertNotifier.notify(Alert.critical("payment.compensation-failed",
					"보상 대기 실패 기록 중 예외 발생: paymentKey=" + paymentKey, "paymentKey=" + paymentKey));
		}
	}

	private void safeCompensationReviewManually(String paymentKey, String detail) {
		try {
			compensationWriter.reviewManually(paymentKey, detail);
		} catch (RuntimeException ex) {
			log.error("보상 대기 수동 확인 기록 중 예외 발생: paymentKey={}", paymentKey, ex);
			alertNotifier.notify(Alert.critical("payment.compensation-failed",
					"보상 대기 수동 확인 기록 중 예외 발생: paymentKey=" + paymentKey, "paymentKey=" + paymentKey));
		}
	}

	private void safeCompensationComplete(String paymentKey, LocalDateTime canceledAt) {
		try {
			compensationWriter.complete(paymentKey, canceledAt);
		} catch (RuntimeException ex) {
			log.error("보상 취소는 성공했으나 보상 대기 반영에 실패함: paymentKey={}", paymentKey, ex);
			alertNotifier.notify(Alert.critical("payment.compensation-failed",
					"보상 취소는 성공했으나 보상 대기 반영에 실패함: paymentKey=" + paymentKey, "paymentKey=" + paymentKey));
		}
	}
}
