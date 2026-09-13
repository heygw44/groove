package com.groove.order.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCancelWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OrderCancelRestorer restorer;

	@Transactional(readOnly = true)
	public OrderCancelTarget findTarget(Long memberId, Long orderId) {
		Order order = orderRepository.findByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		PaymentStatus paymentStatus = paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus())
				.orElse(null);
		return new OrderCancelTarget(order.getStatus(), paymentStatus);
	}

	@Transactional
	public UnpaidCancelResult cancelUnpaid(Long memberId, Long orderId, String reason) {
		Order lockedOrder = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (!lockedOrder.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		Order order = orderRepository.findWithItemsByIdAndMemberId(orderId, memberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Optional<PaymentStatus> paymentStatus = paymentRepository.findByOrderId(orderId)
				.map(payment -> payment.getStatus());
		if (paymentStatus.filter(status -> status == PaymentStatus.DONE
				|| status == PaymentStatus.CANCEL_REQUESTED).isPresent()) {
			return UnpaidCancelResult.paymentCancelRequired();
		}
		order.cancel(reason);
		Long limitedDropId = restorer.restore(order, false).map(LimitedRelease::dropId).orElse(null);
		return UnpaidCancelResult.canceled(limitedDropId);
	}
}
