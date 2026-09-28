package com.groove.payment.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.limited.service.LimitedRelease;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.order.service.OrderCancelRestorer;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * 입금 전 가상계좌 주문 취소의 DB 반영. 토스 계좌 폐쇄는 {@link PendingVirtualAccountCancelService} 가
 * 트랜잭션 밖에서 맡고, 이 클래스는 락·상태 전이·복원만 한다.
 */
@Service
@RequiredArgsConstructor
class PendingVirtualAccountCancelWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OrderCancelRestorer orderCancelRestorer;

	/**
	 * 취소 대상인지 확인하고 paymentKey 를 돌려준다. 토스 호출 전에 상태를 확정 짓지 않아 이 트랜잭션은
	 * 아무것도 쓰지 않는다 - 그 사이 입금이 확인되는 등 상태가 바뀌었으면 예외를 던져 사용자가 다시 요청하게 한다.
	 */
	@Transactional
	public PendingVirtualAccountCancelTarget lock(Long orderId, Long memberId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		if (memberId != null && !order.getMember().getId().equals(memberId)) {
			throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
		}
		Payment payment = paymentRepository.findByOrderId(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.WAITING_FOR_DEPOSIT) {
			throw new BusinessException(ErrorCode.PAYMENT_INVALID_STATUS);
		}
		return new PendingVirtualAccountCancelTarget(payment.getId(), payment.getPaymentKey());
	}

	/** 토스 계좌 폐쇄 뒤 호출한다. 그 사이 입금 확인 등으로 이미 처리됐으면(계좌는 이미 닫혔으니) 조용히 넘어간다. */
	@Transactional
	public Optional<LimitedRelease> finalizeCancel(Long orderId, Long paymentId, String reason,
			LocalDateTime canceledAt) {
		orderRepository.findByIdForUpdate(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Order order = orderRepository.findWithItemsById(orderId)
				.orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.WAITING_FOR_DEPOSIT) {
			return Optional.empty();
		}
		payment.cancelVirtualAccount(reason, canceledAt);
		order.cancel(reason);
		return orderCancelRestorer.restore(order, false);
	}
}
