package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderClaimFinalizeService;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 부분취소를 포함한 결제 환불의 DB 쓰기. 기존 결제 취소 3단계(요청 기록 → 트랜잭션 밖 토스 호출 → 결과 반영)를
 * 따르되, payment.status 는 건드리지 않는다 - CANCEL_REQUESTED 로 옮기면 대사 스케줄러가 legacy 전액취소
 * 재시도 대상으로 집어가 버려, 결과불명일 때 부분취소 건이 전액으로 잘못 재시도된다. 진행 상태는 오직
 * payment_cancel.status(REQUESTED/DONE/FAILED)로만 추적한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final OrderClaimFinalizeService orderClaimFinalizeService;
	private final Clock clock;

	/** payment 행을 잠근 채로 검증·채번하는 이유: 동시에 들어온 두 취소 요청이 같은 순번(idempotencyKey)을 뽑거나 둘 다 통과하지 않게 한다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentRefundRequest requestRefund(Long paymentId, BigDecimal cancelAmount, String reason,
			RefundAccountInfo refundAccount) {
		return requestRefund(paymentId, cancelAmount, reason, refundAccount, null);
	}

	/**
	 * 취소 클레임 승인이 호출하는 경로. {@code orderClaimId} 를 payment_cancel 에 같이 남겨, 결과불명으로
	 * REQUESTED 에 남은 건을 대사({@link PaymentCancelRetrier})가 나중에 확정할 때 어느 클레임을 마무리할지
	 * 찾을 수 있게 한다.
	 *
	 * <p>클레임 환불이면 결제 락보다 먼저 주문 락을 잡고 클레임이 아직 진행 중인지, 이 클레임으로 나간 환불이
	 * 없는지(결과 대기 REQUESTED 나 이미 반영된 DONE) 확인한다. 같은 클레임의 중복 승인, 승인과 철회·거부의
	 * 경합이 이 락 하나로 직렬화되고, 환불이 두 번 나가지 않는다. 락 순서는 다른 결제 경로와 같이 주문 → 결제다.</p>
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public PaymentRefundRequest requestRefund(Long paymentId, BigDecimal cancelAmount, String reason,
			RefundAccountInfo refundAccount, Long orderClaimId) {
		if (orderClaimId != null) {
			orderClaimFinalizeService.lockRefundableClaim(orderClaimId);
			if (paymentCancelRepository.existsByOrderClaimIdAndStatusIn(orderClaimId,
					List.of(PaymentCancelStatus.REQUESTED, PaymentCancelStatus.DONE))) {
				throw new BusinessException(ErrorCode.ORDER_CLAIM_REFUND_IN_PROGRESS);
			}
		}
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
		if (payment.isVirtualAccount() && refundAccount == null) {
			throw new BusinessException(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED);
		}
		if (cancelAmount == null || cancelAmount.signum() <= 0
				|| cancelAmount.compareTo(payment.remainingAmount()) > 0) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_AMOUNT_EXCEEDS_BALANCE);
		}

		String idempotencyKey = PaymentCancelIdempotencyKeys.next(payment.getPaymentKey(), paymentId,
				paymentCancelRepository);
		LocalDateTime requestedAt = LocalDateTime.now(clock);
		PaymentCancel paymentCancel = paymentCancelRepository.save(
				PaymentCancel.requestForClaim(payment, idempotencyKey, cancelAmount, reason, requestedAt,
						orderClaimId));

		return new PaymentRefundRequest(paymentId, paymentCancel.getId(), payment.getPaymentKey(), cancelAmount,
				reason, idempotencyKey, refundAccount, orderClaimId);
	}

	/**
	 * 토스 반영을 기록한다. 클레임 환불이면 클레임 마무리(상품주문 취소·반품 확정, 재고 복원)를 같은 트랜잭션에서
	 * 함께 커밋한다 - 마무리가 실패하면 취소 건도 REQUESTED 로 남아 대사가 같은 멱등키로 다시 이어받는다. 따로
	 * 커밋하면 "환불은 DONE 인데 클레임은 진행 중"인 상태가 남고, 재승인이 환불을 한 번 더 내보낸다.
	 *
	 * <p>즉시 반영과 대사가 같은 취소 건을 동시에 확정할 수 있어 주문 락을 먼저 잡고 payment_cancel 을 그 뒤에
	 * 처음 읽는다. 락 전에 읽어 두면 뒤쪽 트랜잭션이 1차 캐시의 REQUESTED 를 보고 취소액을 한 번 더 더한다.</p>
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void completeRefund(Long paymentId, Long paymentCancelId, BigDecimal cancelAmount, String transactionKey,
			LocalDateTime canceledAt) {
		Long orderId = paymentRepository.findOrderIdById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		PaymentCancel paymentCancel = findPaymentCancel(paymentCancelId);
		if (paymentCancel.getStatus() == PaymentCancelStatus.DONE) {
			log.info("이미 반영된 취소 건이라 건너뜀: paymentCancelId={}", paymentCancelId);
			return;
		}
		if (paymentCancel.getStatus() != PaymentCancelStatus.REQUESTED) {
			log.error("토스 취소는 성공했으나 취소 건이 {} 로 기록돼 있음, 수동 확인 필요: paymentId={}, paymentCancelId={}",
					paymentCancel.getStatus(), paymentId, paymentCancelId);
			return;
		}
		if (paymentCancel.getOrderClaimId() != null) {
			orderClaimFinalizeService.applyRefundDone(paymentCancel.getOrderClaimId(), canceledAt);
		}
		Payment payment = findPayment(paymentId);
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
