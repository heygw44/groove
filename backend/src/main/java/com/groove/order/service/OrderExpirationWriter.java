package com.groove.order.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.order.entity.Order;
import com.groove.order.repository.OrderRepository;
import com.groove.payment.entity.Payment;
import com.groove.payment.entity.PaymentStatus;
import com.groove.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 만료의 DB 반영. 가상계좌 폐쇄(토스 호출)는 {@link OrderExpirationService} 가 트랜잭션 밖에서 맡고, 이
 * 클래스는 락·상태 전이·복원만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationWriter {

	private final OrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final OrderCancelRestorer orderCancelRestorer;

	/**
	 * 만료 대상인지 확인한다. 결제가 WAITING_FOR_DEPOSIT 이면 계좌를 닫아야 해서 여기서는 상태를 바꾸지 않고
	 * 대상 정보만 돌려준다 - 락은 커밋과 함께 풀리고, 토스 호출 뒤 finalizeVirtualAccountExpiry 가 다시 잠가
	 * 확정한다. 그 외에는 이 트랜잭션 안에서 바로 취소·복원까지 끝낸다.
	 */
	@Transactional
	public Optional<OrderExpirationTarget> checkExpirable(Long orderId, LocalDateTime now) {
		Optional<Order> found = orderRepository.findByIdForUpdate(orderId);
		if (found.isEmpty() || !found.get().isExpired(now)) {
			log.debug("만료 대상에서 제외 orderId={}", orderId);
			return Optional.empty();
		}
		Optional<Payment> payment = paymentRepository.findByOrderId(orderId);
		if (payment.isPresent() && payment.get().getStatus().isUnresolved()) {
			log.debug("결제 대사 대기 중이라 만료를 건너뜀 orderId={}", orderId);
			return Optional.empty();
		}
		if (payment.isPresent() && payment.get().getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
			Payment virtualAccountPayment = payment.get();
			return Optional.of(OrderExpirationTarget.virtualAccount(orderId, virtualAccountPayment.getId(),
					virtualAccountPayment.getTossOrderId(), virtualAccountPayment.getPaymentKey()));
		}
		Order order = found.get();
		order.expire(now);
		orderCancelRestorer.restore(order, false);
		return Optional.of(OrderExpirationTarget.simple());
	}

	/** 토스 계좌 폐쇄(또는 이미 닫혀 있음 확인) 뒤 호출한다. 그 사이 입금 확인 등으로 이미 처리됐으면 조용히 넘어간다. */
	@Transactional
	public boolean finalizeVirtualAccountExpiry(Long orderId, Long paymentId, String reason, LocalDateTime now) {
		Optional<Order> found = orderRepository.findByIdForUpdate(orderId);
		if (found.isEmpty() || !found.get().isExpired(now)) {
			return false;
		}
		Payment payment = paymentRepository.findById(paymentId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
		if (payment.getStatus() != PaymentStatus.WAITING_FOR_DEPOSIT) {
			return false;
		}
		Order order = found.get();
		payment.cancelVirtualAccount(reason, now);
		order.expire(now);
		orderCancelRestorer.restore(order, false);
		return true;
	}
}
