package com.groove.payment.service;

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
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCancelWriter {

	private static final String DEFAULT_CANCEL_REASON = "주문 취소";

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OrderCancelRestorer restorer;
	private final Clock clock;

	@Transactional
	public CancelRequest requestCancel(Long orderId, Long memberId, String reason) {
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
			return toRequest(order, payment, previousOrderStatus, true);
		}
		if (payment.getStatus() != PaymentStatus.DONE) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		order.requestCancel(reason, memberId == null);
		payment.requestCancel();
		return toRequest(order, payment, previousOrderStatus, false);
	}

	@Transactional
	public Optional<LimitedRelease> completeCancel(Long orderId, Long paymentId, LocalDateTime canceledAt) {
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
		payment.revertCancelRequest();
		order.withdrawCancelRequest();
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
			boolean alreadyRequested) {
		String cancelReason = order.getCancelReason();
		String tossReason = cancelReason == null || cancelReason.isBlank() ? DEFAULT_CANCEL_REASON : cancelReason;
		return new CancelRequest(order.getId(), payment.getId(), payment.getPaymentKey(), tossReason,
				previousOrderStatus, alreadyRequested);
	}
}
