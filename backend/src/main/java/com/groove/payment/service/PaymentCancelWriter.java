package com.groove.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.entity.OrderStatus;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCancelRestorer;
import com.groove.payment.client.dto.RefundAccountInfo;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentCancel;
import com.groove.payment.entity.PaymentCancelStatus;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentCancelRepository;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 결제 있는 주문의 전액취소 3단계 쓰기. 취소마다 새 idempotencyKey 를 쓰는 {@link PaymentRefundWriter} 와 달리
 * 이 경로는 고정 멱등키({@code cancel-{paymentKey}})를 그대로 쓰지만, 환불 기록은 같은 payment_cancel 행으로
 * 남긴다 - 통계가 이 테이블 하나만 보면 되게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCancelWriter {

	private static final String DEFAULT_CANCEL_REASON = "주문 취소";
	private static final String LEGACY_IDEMPOTENCY_PREFIX = "cancel-";

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentCancelRepository paymentCancelRepository;
	private final OrderCancelRestorer restorer;
	private final Clock clock;

	@Transactional
	public CancelRequest requestCancel(Long orderId, Long memberId, String reason) {
		return requestCancel(orderId, memberId, reason, null);
	}

	@Transactional
	public CancelRequest requestCancel(Long orderId, Long memberId, String reason, RefundAccountInfo refundAccount) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (memberId != null && !order.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		Optional<Payment> foundPayment = paymentRepository.findByOrderId(orderId);
		if (foundPayment.isEmpty()) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
		}
		Payment payment = foundPayment.get();
		OrderStatus previousOrderStatus = order.getStatus();
		if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
			return toRequest(order, payment, previousOrderStatus, true, refundAccount);
		}
		if (payment.getStatus() != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		if (payment.isVirtualAccount() && refundAccount == null) {
			throw new BusinessException(ErrorCode.PAYMENT_REFUND_ACCOUNT_REQUIRED);
		}
		// 부분취소(PaymentRefundWriter)는 이 결제의 status 를 바꾸지 않고 진행되므로, 위 DONE 체크만으로는
		// 진행 중인 부분취소를 걸러내지 못한다 - REQUESTED 행 존재 여부로 직접 겹침을 막는다.
		if (paymentCancelRepository.existsByPaymentIdAndStatus(payment.getId(), PaymentCancelStatus.REQUESTED)) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
		}
		order.requestCancel(reason, memberId == null);
		BigDecimal cancelAmount = payment.remainingAmount();
		payment.requestCancel();
		CancelRequest request = toRequest(order, payment, previousOrderStatus, false, refundAccount);
		paymentCancelRepository.save(PaymentCancel.request(payment, request.idempotencyKey(), cancelAmount,
				request.tossReason(), LocalDateTime.now(clock)));
		return request;
	}

	@Transactional
	public Optional<LimitedRelease> completeCancel(Long orderId, Long paymentId, LocalDateTime canceledAt,
			String transactionKey) {
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Order order = orderRepository.findWithItemsById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = findPayment(orderId, paymentId);
		if (payment.getStatus() == PaymentStatus.CANCELED) {
			return Optional.empty();
		}
		if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		order.completeCancel(LocalDateTime.now(clock));
		Optional<LimitedRelease> limitedRelease = restorer.restore(order, true);
		payment.completeCancel(canceledAt);
		findPaymentCancel(payment).complete(transactionKey, canceledAt);
		return limitedRelease;
	}

	@Transactional
	public void revertCancelRequest(Long orderId, Long paymentId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = findPayment(orderId, paymentId);
		if (payment.getStatus() != PaymentStatus.CANCEL_REQUESTED) {
			return;
		}
		findPaymentCancel(payment).fail();
		payment.revertCancelRequest();
		order.withdrawCancelRequest();
	}

	private PaymentCancel findPaymentCancel(Payment payment) {
		String idempotencyKey = LEGACY_IDEMPOTENCY_PREFIX + payment.getPaymentKey();
		return paymentCancelRepository.findByIdempotencyKey(idempotencyKey)
				.orElseThrow(() -> new IllegalStateException("취소 요청 기록을 찾을 수 없습니다: paymentId=" + payment.getId()));
	}

	private Payment findPayment(Long orderId, Long paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (!payment.getOrder().getId().equals(orderId)) {
			throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
		}
		return payment;
	}

	private CancelRequest toRequest(Order order, Payment payment, OrderStatus previousOrderStatus,
			boolean alreadyRequested, RefundAccountInfo refundAccount) {
		String cancelReason = order.getCancelReason();
		String tossReason = cancelReason == null || cancelReason.isBlank() ? DEFAULT_CANCEL_REASON : cancelReason;
		String idempotencyKey = LEGACY_IDEMPOTENCY_PREFIX + payment.getPaymentKey();
		return new CancelRequest(order.getId(), payment.getId(), payment.getPaymentKey(), tossReason, idempotencyKey,
				previousOrderStatus, alreadyRequested, refundAccount);
	}
}
