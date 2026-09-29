package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 부분취소를 포함한 결제 환불의 DB 쓰기. 기존 결제 취소 3단계(요청 기록 → 트랜잭션 밖 토스 호출 → 결과 반영)를
 * 따르되, payment.status 는 건드리지 않는다 - CANCEL_REQUESTED 로 옮기면 대사 스케줄러가 legacy 전액취소
 * 재시도 대상으로 집어가 버려, 결과불명일 때 부분취소 건이 전액으로 잘못 재시도된다. 진행 상태는 오직
 * payment_cancel.status(REQUESTED/DONE/FAILED)로만 추적한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundWriter {

	private static final String IDEMPOTENCY_KEY_PREFIX = "cancel-";

	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final Clock clock;

	/** payment 행을 잠근 채로 검증·채번하는 이유: 동시에 들어온 두 취소 요청이 같은 순번(idempotencyKey)을 뽑거나 둘 다 통과하지 않게 한다. */
	@Transactional
	public PaymentRefundRequest requestRefund(Long paymentId, BigDecimal cancelAmount, String reason,
			RefundAccountInfo refundAccount) {
		Payment payment = paymentRepository.findByIdForUpdate(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.DONE && payment.getStatus() != PaymentStatus.PARTIAL_CANCELED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		// 전액취소(PaymentCancelWriter)도 이 결제를 CANCEL_REQUESTED 로 옮겨 잠글 수 있지만, 그 경로는 status
		// 로 겹침이 드러난다. 부분취소끼리, 또는 부분취소와 전액취소 사이의 겹침은 status 만으로 안 드러나므로
		// 진행 중(REQUESTED) payment_cancel 행이 있는지로 직접 막는다.
		if (paymentCancelRepository.existsByPaymentIdAndStatus(paymentId, PaymentCancelStatus.REQUESTED)) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
		}
		if (cancelAmount == null || cancelAmount.signum() <= 0
				|| cancelAmount.compareTo(payment.remainingAmount()) > 0) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDS_BALANCE);
		}

		long sequence = paymentCancelRepository.countByPaymentId(paymentId) + 1;
		String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + payment.getPaymentKey() + "-" + sequence;
		LocalDateTime requestedAt = LocalDateTime.now(clock);
		PaymentCancel paymentCancel = paymentCancelRepository.save(
				PaymentCancel.request(payment, idempotencyKey, cancelAmount, reason, requestedAt));

		return new PaymentRefundRequest(paymentId, paymentCancel.getId(), payment.getPaymentKey(), cancelAmount,
				reason, idempotencyKey, refundAccount);
	}

	@Transactional
	public void completeRefund(Long paymentId, Long paymentCancelId, BigDecimal cancelAmount, String transactionKey,
			LocalDateTime canceledAt) {
		Payment payment = findPayment(paymentId);
		PaymentCancel paymentCancel = findPaymentCancel(paymentCancelId);
		payment.applyPartialCancel(cancelAmount, canceledAt);
		paymentCancel.complete(transactionKey, canceledAt);
	}

	/**
	 * 토스가 명시적으로 거절했을 때 취소 건만 FAILED 로 남긴다. payment.status 는 애초에 REQUESTED 단계에서
	 * 바뀐 적이 없어 되돌릴 것도 없다.
	 */
	@Transactional
	public void failRefund(Long paymentCancelId) {
		findPaymentCancel(paymentCancelId).fail();
	}

	private Payment findPayment(Long paymentId) {
		return paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}

	private PaymentCancel findPaymentCancel(Long paymentCancelId) {
		return paymentCancelRepository.findById(paymentCancelId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
	}
}
